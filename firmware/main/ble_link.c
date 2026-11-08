#include <stdio.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include "esp_log.h"
#include "esp_ota_ops.h"
#include "esp_system.h"
#include "mbedtls/sha256.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/ble_store.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "board.h"
#include "ble_link.h"
#include "proto.h"
#include "auth.h"
#include "vstate.h"
#include "logger.h"
#include "config.h"
#include "obd.h"
#include "vguard.h"
#include "fuel.h"
#include "sensors.h"
#include "trip.h"

static const char *TAG = "ble";
void ble_store_config_init(void);

#define UUID_LL(x) BLE_UUID128_INIT(0x00,0x00,0x00,0x6b,0x6e,0x69,0x6c,0x6e,0x61,0x67,0x6f,0x6c,(x),0x00,0x4c,0x4c)
static const ble_uuid128_t SVC_UUID = UUID_LL(0x01), TX_UUID = UUID_LL(0x02), RX_UUID = UUID_LL(0x03), OTA_UUID = UUID_LL(0x04);

#define MAX_CONN 2
typedef struct {
    bool used, subscribed;
    uint16_t handle, tx_seq;
    uint8_t live_hz;
    int bonds_at_connect;
    auth_session_t auth;
    uint32_t clear_token, clear_token_until;
    uint32_t sync_next, sync_end;
} conn_t;

typedef struct { uint16_t conn; uint16_t len; uint8_t chr; uint8_t data[PROTO_MAX_FRAME]; } inmsg_t;

static conn_t s_conn[MAX_CONN];
static uint16_t s_tx_handle;
static QueueHandle_t s_q;
static uint8_t s_own_addr_type;
static uint32_t s_pair_until;
static bool s_adv_enabled = true;
static esp_ota_handle_t s_ota; static const esp_partition_t *s_ota_part;
static uint32_t s_ota_size, s_ota_off; static uint8_t s_ota_sha[32]; static mbedtls_sha256_context s_sha;

static void advertise(void);

static conn_t *find_conn(uint16_t h)
{
    for (int i = 0; i < MAX_CONN; i++) if (s_conn[i].used && s_conn[i].handle == h) return &s_conn[i];
    return NULL;
}

bool ble_link_any_connected(void)
{
    for (int i = 0; i < MAX_CONN; i++) if (s_conn[i].used) return true;
    return false;
}

void ble_link_open_pairing_window(uint32_t seconds) { s_pair_until = now_ms() + seconds * 1000; ESP_LOGI(TAG, "pairing window %lus", (unsigned long)seconds); }
static bool pairing_open(void) { return now_ms() < s_pair_until; }

static int send_frame(conn_t *c, uint8_t type, uint8_t flags, const uint8_t *pl, uint16_t len)
{
    uint8_t f[PROTO_MAX_FRAME];
    size_t n = proto_build(f, type, flags, c->tx_seq++, pl, len);
    struct os_mbuf *om = ble_hs_mbuf_from_flat(f, n);
    if (!om) return BLE_HS_ENOMEM;
    return ble_gatts_notify_custom(c->handle, s_tx_handle, om);
}

static void rsp(conn_t *c, uint32_t ctr, uint8_t cmd, uint8_t status, const uint8_t *d, uint16_t dl)
{
    uint8_t p[PROTO_MAX_PAYLOAD];
    put_u32(p, ctr); p[4] = cmd; p[5] = status;
    if (dl > PROTO_MAX_PAYLOAD - 6) dl = PROTO_MAX_PAYLOAD - 6;
    if (dl) memcpy(p + 6, d, dl);
    send_frame(c, FT_RSP, 0, p, 6 + dl);
}

static bool owner_only(uint8_t cmd)
{
    switch (cmd) {
    case CMD_CLEAR_DTC: case CMD_RUN_PROBE: case CMD_SET_CONFIG: case CMD_SET_ODOMETER: case CMD_CAL_VBAT:
    case CMD_CAL_FUEL: case CMD_AUX_OUT: case CMD_OTA_BEGIN: case CMD_OTA_END: case CMD_WIFI_OTA:
    case CMD_USER_ADD: case CMD_USER_REVOKE: case CMD_REBOOT: return true;
    default: return false;
    }
}

static void probe_task(void *arg) { obd_run_probe(); vTaskDelete(NULL); }

static void handle_cmd(conn_t *c, const uint8_t *frame, size_t flen, const proto_frame_t *f)
{
    if (!auth_check_cmd(&c->auth, frame, flen - PROTO_CRC_LEN)) {
        rsp(c, f->len >= 4 ? get_u32(f->payload) : 0, f->len > 4 ? f->payload[4] : 0, RS_NOT_AUTH, NULL, 0);
        return;
    }
    uint32_t ctr = get_u32(f->payload);
    uint8_t cmd = f->payload[4];
    const uint8_t *a = f->payload + 5;
    size_t al = f->len - 5 - PROTO_MAC_LEN;
    if (owner_only(cmd) && c->auth.role != 0) { rsp(c, ctr, cmd, RS_FORBIDDEN, NULL, 0); return; }
    vstate_t v; vstate_snapshot(&v);
    bool engine_running = (v.rpm != 0xFFFF && v.rpm > 300) || v.vbat_mv > 13200;
    uint8_t out[PROTO_MAX_PAYLOAD]; uint16_t ol = 0;

    switch (cmd) {
    case CMD_GET_INFO: {
        const obd_profile_t *p = obd_profile();
        out[ol++] = FW_VERSION_MAJOR; out[ol++] = FW_VERSION_MINOR; out[ol++] = FW_VERSION_PATCH; out[ol++] = HW_REVISION;
        out[ol++] = p->proto; out[ol++] = p->target; out[ol++] = p->kb1; out[ol++] = p->kb2;
        for (int i = 0; i < 4; i++) { put_u32(out + ol, p->pid_support[i]); ol += 4; }
        put_u32(out + ol, logger_oldest_seq()); ol += 4;
        put_u32(out + ol, logger_head_seq()); ol += 4;
        put_u32(out + ol, vguard_denied_count()); ol += 4;
        size_t idl = strnlen(p->ecu_id, sizeof p->ecu_id); out[ol++] = idl;
        memcpy(out + ol, p->ecu_id, idl); ol += idl;
        size_t vl = strnlen(p->vin, sizeof p->vin); out[ol++] = vl;
        memcpy(out + ol, p->vin, vl); ol += vl;
        rsp(c, ctr, cmd, RS_OK, out, ol); break;
    }
    case CMD_SET_TIME:
        if (al < 4) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        if (!(v.src & SRC_GNSS_VALID)) { VS_SET(utc, get_u32(a)); rtc_set_utc(get_u32(a)); }
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    case CMD_SET_LIVE_RATE:
        c->live_hz = (al >= 1 && a[0] <= 10) ? a[0] : 10;
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    case CMD_READ_DTC: {
        obd_dtc_t d[OBD_MAX_DTC];
        int n = obd_read_dtcs(al >= 1 && a[0] == 1, d, OBD_MAX_DTC);
        if (n < 0) { rsp(c, ctr, cmd, RS_IO_ERROR, NULL, 0); break; }
        out[ol++] = n; out[ol++] = 0 ;
        for (int i = 0; i < n; i++) { put_u16(out + ol, d[i].code); ol += 2; out[ol++] = d[i].status; }
        send_frame(c, FT_DTC_LIST, 0, out, ol);
        if (n) logger_write(REC_DTC, out, ol);
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    }
    case CMD_CLEAR_DTC:
        if (engine_running) { rsp(c, ctr, cmd, RS_VEHICLE_STATE, NULL, 0); break; }
        if (al >= 4 && get_u32(a) == c->clear_token && now_ms() < c->clear_token_until && c->clear_token) {
            c->clear_token = 0;
            vguard_arm_clear_dtc();
            rsp(c, ctr, cmd, obd_clear_dtcs() == ESP_OK ? RS_OK : RS_IO_ERROR, NULL, 0);
        } else {
            c->clear_token = esp_random() | 1; c->clear_token_until = now_ms() + 30000;
            put_u32(out, c->clear_token);
            rsp(c, ctr, cmd, RS_OK, out, 4);
        }
        break;
    case CMD_RUN_PROBE:
        xTaskCreatePinnedToCore(probe_task, "probe", 6144, NULL, 9, NULL, 1);
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    case CMD_SYNC_LOG: {
        if (al < 6) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        uint32_t from = get_u32(a); uint16_t max = get_u16(a + 4);
        if (from < logger_oldest_seq()) from = logger_oldest_seq();
        c->sync_next = from;
        c->sync_end = from + max < logger_head_seq() ? from + max : logger_head_seq();
        put_u32(out, c->sync_next); put_u32(out + 4, c->sync_end);
        rsp(c, ctr, cmd, RS_OK, out, 8); break;
    }
    case CMD_GET_CONFIG:
        if (al < 2) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        put_u32(out, (uint32_t)config_get(get_u16(a)));
        rsp(c, ctr, cmd, RS_OK, out, 4); break;
    case CMD_SET_CONFIG:
        if (al < 6) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        rsp(c, ctr, cmd, config_set(get_u16(a), (int32_t)get_u32(a + 2)) == ESP_OK ? RS_OK : RS_BAD_ARGS, NULL, 0); break;
    case CMD_SET_ODOMETER:
        if (al < 4) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        config_set(CFG_ODOMETER_KM, (int32_t)get_u32(a));
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    case CMD_CAL_VBAT: {
        if (al < 2 || v.vbat_mv < 9000) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        int64_t g = (int64_t)config_get(CFG_VBAT_GAIN_PPM) * get_u16(a) / v.vbat_mv;
        config_set(CFG_VBAT_GAIN_PPM, (int32_t)g);
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    }
    case CMD_FILL_UP:
        if (al < 6) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        fuel_fill_up(get_u16(a) / 100.f);
        logger_write(REC_FILL, a, 6);
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    case CMD_AUX_OUT:
        rsp(c, ctr, cmd, RS_UNSUPPORTED, NULL, 0); break;
    case CMD_OTA_BEGIN:
        if (al < 36 || engine_running || v.vbat_mv < 12200) { rsp(c, ctr, cmd, RS_VEHICLE_STATE, NULL, 0); break; }
        s_ota_part = esp_ota_get_next_update_partition(NULL);
        s_ota_size = get_u32(a); memcpy(s_ota_sha, a + 4, 32); s_ota_off = 0;
        if (!s_ota_part || s_ota_size > s_ota_part->size || esp_ota_begin(s_ota_part, s_ota_size, &s_ota) != ESP_OK) {
            rsp(c, ctr, cmd, RS_IO_ERROR, NULL, 0); break;
        }
        mbedtls_sha256_init(&s_sha); mbedtls_sha256_starts(&s_sha, 0);
        rsp(c, ctr, cmd, RS_OK, NULL, 0); break;
    case CMD_OTA_END: {
        uint8_t h[32];
        mbedtls_sha256_finish(&s_sha, h); mbedtls_sha256_free(&s_sha);
        bool ok = s_ota_off == s_ota_size && memcmp(h, s_ota_sha, 32) == 0 && esp_ota_end(s_ota) == ESP_OK
                  && esp_ota_set_boot_partition(s_ota_part) == ESP_OK;
        rsp(c, ctr, cmd, ok ? RS_OK : RS_IO_ERROR, NULL, 0);
        if (ok) { vTaskDelay(pdMS_TO_TICKS(500)); esp_restart(); }
        else if (s_ota) esp_ota_abort(s_ota);
        s_ota = 0;
        break;
    }
    case CMD_USER_ADD:
        if (al < 1 || a[0] == 0 || a[0] > 3) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        config_unrevoke_user(a[0]);
        auth_derive_user_key(a[0], out);
        rsp(c, ctr, cmd, RS_OK, out, 32);
        memset(out, 0, 32);
        break;
    case CMD_USER_REVOKE:
        if (al < 1) { rsp(c, ctr, cmd, RS_BAD_ARGS, NULL, 0); break; }
        rsp(c, ctr, cmd, config_revoke_user(a[0]) == ESP_OK ? RS_OK : RS_BAD_ARGS, NULL, 0); break;
    case CMD_REBOOT:
        rsp(c, ctr, cmd, RS_OK, NULL, 0); vTaskDelay(pdMS_TO_TICKS(300)); esp_restart(); break;
    default:
        rsp(c, ctr, cmd, RS_UNSUPPORTED, NULL, 0);
    }
}

static void handle_ota_data(conn_t *c, const proto_frame_t *f)
{
    if (!s_ota || !c->auth.authed || c->auth.role != 0 || f->len < 5) return;
    uint32_t off = get_u32(f->payload);
    if (off != s_ota_off) return;
    const uint8_t *d = f->payload + 4; size_t n = f->len - 4;
    if (esp_ota_write(s_ota, d, n) != ESP_OK) { esp_ota_abort(s_ota); s_ota = 0; return; }
    mbedtls_sha256_update(&s_sha, d, n);
    s_ota_off += n;
    static uint32_t chunks;
    if (++chunks % 32 == 0 || s_ota_off == s_ota_size) {
        uint8_t ack[10];
        put_u32(ack, 0); ack[4] = CMD_OTA_BEGIN; ack[5] = RS_OK; put_u32(ack + 6, s_ota_off);
        send_frame(c, FT_RSP, 0, ack, sizeof ack);
    }
}

static void worker_task(void *arg)
{
    inmsg_t m;
    uint32_t last_live = 0, last_log = 0;
    for (;;) {
        if (xQueueReceive(s_q, &m, pdMS_TO_TICKS(10)) == pdTRUE) {
            conn_t *c = find_conn(m.conn);
            proto_frame_t f;
            if (c && proto_parse(m.data, m.len, &f)) {
                if (f.type == FT_AUTH_RESPONSE) {
                    bool ok = auth_check_response(&c->auth, f.payload, f.len);
                    uint8_t r[2] = { ok ? RS_OK : RS_NOT_AUTH, c->auth.role };
                    send_frame(c, FT_AUTH_RESULT, 0, r, 2);
                    if (!ok) { ESP_LOGW(TAG, "auth failed, dropping link"); ble_gap_terminate(c->handle, BLE_ERR_AUTH_FAIL); }
                } else if (f.type == FT_CMD) handle_cmd(c, m.data, m.len, &f);
                else if (f.type == FT_OTA_DATA) handle_ota_data(c, &f);
            }
        }
        uint32_t t = now_ms();
        if (t - last_live >= 100) {
            last_live = t;
            vstate_t v; vstate_snapshot(&v);
            uint8_t pl[LIVE_V1_LEN]; proto_pack_live(&v, pl);
            static uint32_t tick; tick++;
            for (int i = 0; i < MAX_CONN; i++) {
                conn_t *c = &s_conn[i];
                if (c->used && c->subscribed && c->auth.authed && c->live_hz && tick % (10 / c->live_hz) == 0)
                    send_frame(c, FT_LIVE, 0, pl, LIVE_V1_LEN);
            }
        }
        if (t != last_log) {
            last_log = t;
            for (int i = 0; i < MAX_CONN; i++) {
                conn_t *c = &s_conn[i];
                for (int burst = 0; burst < 4 && c->used && c->auth.authed && c->sync_next < c->sync_end; burst++) {
                    log_rec_t r; uint8_t pl[5 + LOG_REC_SIZE];
                    if (logger_read(c->sync_next, &r) == ESP_OK) {
                        put_u32(pl, c->sync_next); pl[4] = 1; memcpy(pl + 5, &r, LOG_REC_SIZE);
                        if (send_frame(c, FT_LOG_CHUNK, 0, pl, sizeof pl) != 0) break;
                    }
                    c->sync_next++;
                }
            }
        }
    }
}

static int gatt_access(uint16_t conn, uint16_t attr, struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) return BLE_ATT_ERR_UNLIKELY;
    inmsg_t m = { .conn = conn, .chr = (uint8_t)(uintptr_t)arg };
    uint16_t n = OS_MBUF_PKTLEN(ctxt->om);
    if (n > sizeof m.data) return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    ble_hs_mbuf_to_flat(ctxt->om, m.data, sizeof m.data, &m.len);
    return xQueueSend(s_q, &m, 0) == pdTRUE ? 0 : BLE_ATT_ERR_PREPARE_QUEUE_FULL;
}

static const struct ble_gatt_svc_def s_svcs[] = {
    { .type = BLE_GATT_SVC_TYPE_PRIMARY, .uuid = &SVC_UUID.u,
      .characteristics = (struct ble_gatt_chr_def[]) {
          { .uuid = &TX_UUID.u, .access_cb = gatt_access, .flags = BLE_GATT_CHR_F_NOTIFY, .val_handle = &s_tx_handle },
          { .uuid = &RX_UUID.u, .access_cb = gatt_access, .arg = (void *)1,
            .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_NO_RSP | BLE_GATT_CHR_F_WRITE_ENC },
          { .uuid = &OTA_UUID.u, .access_cb = gatt_access, .arg = (void *)2,
            .flags = BLE_GATT_CHR_F_WRITE_NO_RSP | BLE_GATT_CHR_F_WRITE_ENC },
          { 0 } } },
    { 0 },
};

static int bond_count(void) { int n = 0; ble_store_util_count(BLE_STORE_OBJ_TYPE_PEER_SEC, &n); return n; }

static int gap_event(struct ble_gap_event *e, void *arg)
{
    switch (e->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (e->connect.status == 0) {
            for (int i = 0; i < MAX_CONN; i++) if (!s_conn[i].used) {
                memset(&s_conn[i], 0, sizeof s_conn[i]);
                s_conn[i].used = true; s_conn[i].handle = e->connect.conn_handle; s_conn[i].live_hz = 10;
                s_conn[i].bonds_at_connect = bond_count();
                ble_gap_security_initiate(e->connect.conn_handle);
                break;
            }
        }
        advertise();
        break;
    case BLE_GAP_EVENT_ENC_CHANGE: {
        conn_t *c = find_conn(e->enc_change.conn_handle);
        if (!c) break;
        if (e->enc_change.status != 0) { ble_gap_terminate(c->handle, BLE_ERR_AUTH_FAIL); break; }
        if (bond_count() > c->bonds_at_connect && !pairing_open()) {
            struct ble_gap_conn_desc d;
            if (ble_gap_conn_find(c->handle, &d) == 0) ble_store_util_delete_peer(&d.peer_id_addr);
            ESP_LOGW(TAG, "pairing attempt outside window rejected");
            ble_gap_terminate(c->handle, BLE_ERR_AUTH_FAIL);
            break;
        }
        uint8_t ch[24]; auth_new_challenge(&c->auth, ch);
        send_frame(c, FT_AUTH_CHALLENGE, 0, ch, sizeof ch);
        ble_gap_set_prefered_le_phy(c->handle, BLE_GAP_LE_PHY_2M_MASK, BLE_GAP_LE_PHY_2M_MASK, 0);
        break;
    }
    case BLE_GAP_EVENT_SUBSCRIBE: {
        conn_t *c = find_conn(e->subscribe.conn_handle);
        if (c && e->subscribe.attr_handle == s_tx_handle) {
            c->subscribed = e->subscribe.cur_notify;
            if (c->subscribed && !c->auth.authed) { uint8_t ch[24]; auth_new_challenge(&c->auth, ch); send_frame(c, FT_AUTH_CHALLENGE, 0, ch, sizeof ch); }
        }
        break;
    }
    case BLE_GAP_EVENT_DISCONNECT: {
        conn_t *c = find_conn(e->disconnect.conn.conn_handle);
        if (c) { memset(&c->auth, 0, sizeof c->auth); c->used = false; }
        advertise();
        break;
    }
    case BLE_GAP_EVENT_REPEAT_PAIRING: {
        struct ble_gap_conn_desc d;
        ble_gap_conn_find(e->repeat_pairing.conn_handle, &d);
        if (!pairing_open()) return BLE_GAP_REPEAT_PAIRING_IGNORE;
        ble_store_util_delete_peer(&d.peer_id_addr);
        return BLE_GAP_REPEAT_PAIRING_RETRY;
    }
    case BLE_GAP_EVENT_ADV_COMPLETE: advertise(); break;
    }
    return 0;
}

static void advertise(void)
{
    if (!s_adv_enabled || ble_gap_adv_active()) return;
    int used = 0; for (int i = 0; i < MAX_CONN; i++) used += s_conn[i].used;
    if (used >= MAX_CONN) return;
    vstate_t v; vstate_snapshot(&v);
    uint8_t mfg[5] = { 0xFF, 0xFF, PROTO_VERSION, v.state, (uint8_t)(v.vbat_mv / 100) };
    struct ble_hs_adv_fields f = { 0 };
    f.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    f.uuids128 = (ble_uuid128_t *)&SVC_UUID; f.num_uuids128 = 1; f.uuids128_is_complete = 1;
    f.mfg_data = mfg; f.mfg_data_len = sizeof mfg;
    ble_gap_adv_set_fields(&f);
    struct ble_hs_adv_fields sr = { 0 };
    const char *name = ble_svc_gap_device_name();
    sr.name = (const uint8_t *)name; sr.name_len = strlen(name); sr.name_is_complete = 1;
    ble_gap_adv_rsp_set_fields(&sr);
    struct ble_gap_adv_params p = { .conn_mode = BLE_GAP_CONN_MODE_UND, .disc_mode = BLE_GAP_DISC_MODE_GEN,
                                    .itvl_min = BLE_GAP_ADV_ITVL_MS(100), .itvl_max = BLE_GAP_ADV_ITVL_MS(300) };
    ble_gap_adv_start(s_own_addr_type, NULL, BLE_HS_FOREVER, &p, gap_event, NULL);
}

void ble_link_set_advertising(bool on)
{
    s_adv_enabled = on;
    if (on) advertise(); else if (ble_gap_adv_active()) ble_gap_adv_stop();
}

static void on_sync(void)
{
    ble_hs_id_infer_auto(0, &s_own_addr_type);
    uint8_t mac[6]; ble_hs_id_copy_addr(s_own_addr_type, mac, NULL);
    char name[20]; snprintf(name, sizeof name, "LoganLink-%02X%02X", mac[1], mac[0]);
    ble_svc_gap_device_name_set(name);
    if (bond_count() == 0) ble_link_open_pairing_window(600);
    advertise();
}

static void host_task(void *p) { nimble_port_run(); nimble_port_freertos_deinit(); }

void ble_link_start(void)
{
    s_q = xQueueCreate(8, sizeof(inmsg_t));
    ESP_ERROR_CHECK(nimble_port_init());
    ble_hs_cfg.sync_cb = on_sync;
    ble_hs_cfg.store_status_cb = ble_store_util_status_rr;
    ble_hs_cfg.sm_bonding = 1; ble_hs_cfg.sm_sc = 1; ble_hs_cfg.sm_mitm = 0;
    ble_hs_cfg.sm_io_cap = BLE_HS_IO_NO_INPUT_OUTPUT;
    ble_hs_cfg.sm_our_key_dist = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
    ble_hs_cfg.sm_their_key_dist = BLE_SM_PAIR_KEY_DIST_ENC | BLE_SM_PAIR_KEY_DIST_ID;
    ble_svc_gap_init(); ble_svc_gatt_init();
    ESP_ERROR_CHECK(ble_gatts_count_cfg(s_svcs));
    ESP_ERROR_CHECK(ble_gatts_add_svcs(s_svcs));
    ble_store_config_init();
    xTaskCreatePinnedToCore(worker_task, "link", 8192, NULL, 9, NULL, 0);
    nimble_port_freertos_init(host_task);
}
