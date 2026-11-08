#include <string.h>
#include <ctype.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "obd.h"
#include "kline.h"
#include "vstate.h"
#include "config.h"
#include "logger.h"

static const char *TAG = "obd";
static obd_profile_t s_prof;
static kl_session_t  s_sess;
static SemaphoreHandle_t s_bus;
static bool s_online;

const obd_profile_t *obd_profile(void) { return &s_prof; }
bool obd_online(void) { return s_online; }

typedef struct { uint8_t pid; uint16_t period_ms; uint32_t last; } sched_t;
static sched_t s_sched[] = {
    { 0x0C, 200, 0 },
    { 0x0D, 200, 0 },
    { 0x0B, 500, 0 },
    { 0x11, 500, 0 },
    { 0x04, 1000, 0 },
    { 0x0E, 1000, 0 },
    { 0x05, 2000, 0 },
    { 0x0F, 2000, 0 },
    { 0x06, 2000, 0 },
    { 0x07, 5000, 0 },
    { 0x01, 30000, 0 },
};

static bool pid_supported(uint8_t pid)
{
    if (pid == 0x00) return true;
    uint8_t blk = (pid - 1) / 32, bit = (pid - 1) % 32;
    return blk < 4 && (s_prof.pid_support[blk] & (0x80000000u >> bit));
}

static esp_err_t req(const uint8_t *r, size_t rl, uint8_t *resp, size_t *n)
{
    xSemaphoreTake(s_bus, portMAX_DELAY);
    esp_err_t e = kl_request(&s_sess, r, rl, resp, 64, n);
    xSemaphoreGive(s_bus);
    return e;
}

static void decode_pid(uint8_t pid, const uint8_t *d, size_t n)
{
    uint8_t A = n > 0 ? d[0] : 0, B = n > 1 ? d[1] : 0;
    vstate_lock();
    vstate_t *v = vstate_raw();
    switch (pid) {
    case 0x01: v->dtc = ((A & 0x80) ? 0x8000 : 0) | (A & 0x7F); break;
    case 0x04: v->load = (uint8_t)(A * 100 / 255); break;
    case 0x05: v->ect_x10 = (int16_t)((A - 40) * 10); break;
    case 0x06: v->stft = (int8_t)(((int)A - 128) * 100 / 128); break;
    case 0x07: v->ltft = (int8_t)(((int)A - 128) * 100 / 128); break;
    case 0x0B: v->map_x10 = A * 10; break;
    case 0x0C: v->rpm = (uint16_t)(((A << 8) | B) / 4);
               v->src = (v->src | SRC_RPM_ECU) & ~SRC_RPM_RIPPLE; v->ecu_rpm_ms = now_ms(); break;
    case 0x0D: v->speed_x10 = A * 10; v->src |= SRC_SPEED_ECU; v->ecu_speed_ms = now_ms(); break;
    case 0x0E: v->adv = (int8_t)(A / 2 - 64); break;
    case 0x0F: v->iat_x10 = (int16_t)((A - 40) * 10); break;
    case 0x11: v->tps_x10 = (uint16_t)(A * 1000 / 255); break;
    }
    vstate_unlock();
}

static void apply_lid(uint8_t lid, const uint8_t *d, size_t n)
{
    const lidmap_t *m; int cnt = config_lidmap(&m);
    for (int i = 0; i < cnt; i++) {
        if (m[i].lid != lid || m[i].off + m[i].size > n) continue;
        int32_t raw = 0;
        for (int k = 0; k < m[i].size; k++) raw = (raw << 8) | d[m[i].off + k];
        if (m[i].is_signed && m[i].size < 4 && (raw & (1 << (m[i].size * 8 - 1))))
            raw -= (1 << (m[i].size * 8));
        float val = raw * m[i].scale + m[i].add;
        vstate_lock();
        vstate_t *v = vstate_raw();
        switch (m[i].field) {
        case LF_RPM:     v->rpm = (uint16_t)val; v->src |= SRC_RPM_ECU; v->ecu_rpm_ms = now_ms(); break;
        case LF_SPEED:   v->speed_x10 = (uint16_t)(val * 10); v->src |= SRC_SPEED_ECU; v->ecu_speed_ms = now_ms(); break;
        case LF_ECT:     v->ect_x10 = (int16_t)(val * 10); break;
        case LF_IAT:     v->iat_x10 = (int16_t)(val * 10); break;
        case LF_MAP:     v->map_x10 = (uint16_t)(val * 10); break;
        case LF_TPS:     v->tps_x10 = (uint16_t)(val * 10); break;
        case LF_ADV:     v->adv = (int8_t)val; break;
        case LF_STFT:    v->stft = (int8_t)val; break;
        case LF_LTFT:    v->ltft = (int8_t)val; break;
        }
        vstate_unlock();
    }
}

static esp_err_t try_connect(uint8_t proto, uint8_t target, uint8_t functional)
{
    memset(&s_sess, 0, sizeof s_sess);
    s_sess.proto = proto; s_sess.target = target; s_sess.functional = functional;
    return kl_connect(&s_sess);
}

static void read_vin(void)
{
    if (s_prof.proto == 0) return;
    uint8_t q[2] = { 0x09, 0x02 }, r[64]; size_t n;
    char raw[24] = { 0 }; size_t got = 0;
    xSemaphoreTake(s_bus, portMAX_DELAY);
    esp_err_t e = kl_request(&s_sess, q, 2, r, sizeof r, &n);
    for (int i = 0; e == ESP_OK && i < 6; i++) {
        if (n >= 7 && r[0] == 0x49 && r[1] == 0x02) {
            for (size_t k = 3; k < n && got < sizeof raw - 1; k++) if (r[k]) raw[got++] = (char)r[k];
        } else if (n >= 4 && r[0] == 0x49 && r[1] == 0x02) {
            for (size_t k = 2; k < n && got < sizeof raw - 1; k++) if (r[k] >= '0') raw[got++] = (char)r[k];
        }
        e = kl_read_next(&s_sess, r, sizeof r, &n, 120);
    }
    xSemaphoreGive(s_bus);
    if (got >= 17) memcpy(s_prof.vin, raw + got - 17, 17);
    s_prof.vin[17] = 0;
}

esp_err_t obd_run_probe(void)
{
    static const struct { uint8_t proto, target, functional; } cand[] = {
        { KL_PROTO_KWP_FAST,  0x33, 1 },
        { KL_PROTO_KWP_5BAUD, 0x33, 1 },
        { KL_PROTO_ISO9141,   0x33, 1 },
    };
    xSemaphoreTake(s_bus, portMAX_DELAY);
    memset(&s_prof, 0, sizeof s_prof);
    bool ok = false;
    for (size_t i = 0; i < sizeof cand / sizeof cand[0] && !ok; i++) {
        ok = try_connect(cand[i].proto, cand[i].target, cand[i].functional) == ESP_OK;
        if (ok) { s_prof.proto = cand[i].proto; s_prof.target = cand[i].target; s_prof.functional = 1; }
        else vTaskDelay(pdMS_TO_TICKS(3000));
    }
    const uint8_t *phys; int np = config_ecu_candidates(&phys);
    for (int i = 0; i < np && !ok; i++) {
        ok = try_connect(KL_PROTO_KWP_FAST, phys[i], 0) == ESP_OK;
        if (ok) { s_prof.proto = KL_PROTO_KWP_FAST; s_prof.target = phys[i]; s_prof.functional = 0; }
        else vTaskDelay(pdMS_TO_TICKS(3000));
    }
    xSemaphoreGive(s_bus);
    if (!ok) { ESP_LOGW(TAG, "probe: no ECU answered"); return ESP_ERR_NOT_FOUND; }
    s_prof.kb1 = s_sess.kb1; s_prof.kb2 = s_sess.kb2;

    uint8_t r[64]; size_t n;
    for (int blk = 0; blk < 4; blk++) {
        uint8_t q[2] = { 0x01, (uint8_t)(blk * 0x20) };
        if (blk && !(s_prof.pid_support[blk - 1] & 1)) break;
        if (req(q, 2, r, &n) == ESP_OK && n >= 6)
            s_prof.pid_support[blk] = ((uint32_t)r[2] << 24) | (r[3] << 16) | (r[4] << 8) | r[5];
    }
    if (s_prof.proto != KL_PROTO_ISO9141) {
        uint8_t q[2] = { 0x1A, 0x80 };
        if (req(q, 2, r, &n) == ESP_OK) {
            size_t k = 0;
            for (size_t j = 2; j < n && k < sizeof s_prof.ecu_id - 1; j++)
                if (isprint(r[j])) s_prof.ecu_id[k++] = r[j];
        }
        for (uint8_t lid = 0x01; lid < 0x10; lid++) {
            uint8_t ql[2] = { 0x21, lid };
            if (req(ql, 2, r, &n) == ESP_OK) {
                s_prof.lid_present[lid] = 1;
                logger_write(REC_LID_DUMP, r, n);
            }
        }
    }
    read_vin();
    config_save_profile(&s_prof, sizeof s_prof);
    ESP_LOGI(TAG, "probe ok proto=%d tgt=%02X pids=%08lX %08lX ecu='%s'", s_prof.proto, s_prof.target,
             (unsigned long)s_prof.pid_support[0], (unsigned long)s_prof.pid_support[1], s_prof.ecu_id);
    return ESP_OK;
}

void obd_dtc_to_str(uint16_t c, char out[6])
{
    static const char sys[] = "PCBU";
    static const char hex[] = "0123456789ABCDEF";
    out[0] = sys[(c >> 14) & 3]; out[1] = hex[(c >> 12) & 3];
    out[2] = hex[(c >> 8) & 0xF]; out[3] = hex[(c >> 4) & 0xF]; out[4] = hex[c & 0xF]; out[5] = 0;
}

int obd_read_dtcs(bool pending, obd_dtc_t *out, int max)
{
    if (!s_online) return -1;
    uint8_t r[64]; size_t n; int cnt = 0;
    uint8_t q[1] = { pending ? 0x07 : 0x03 };
    if (req(q, 1, r, &n) != ESP_OK) return -1;
    for (size_t i = 1; i + 1 < n && cnt < max; i += 2) {
        uint16_t code = (r[i] << 8) | r[i + 1];
        if (code) { out[cnt].code = code; out[cnt].status = pending ? 0x04 : 0x08; cnt++; }
    }
    return cnt;
}

esp_err_t obd_clear_dtcs(void)
{
    if (!s_online) return ESP_ERR_INVALID_STATE;
    uint8_t r[8]; size_t n;
    uint8_t q[1] = { 0x04 };
    return req(q, 1, r, &n);
}

static void vehicle_task(void *arg)
{
    int fails = 0;
    uint32_t backoff_until = 0, last_lid = 0;
    if (config_load_profile(&s_prof, sizeof s_prof) != ESP_OK) memset(&s_prof, 0, sizeof s_prof);

    for (;;) {
        vstate_t v; vstate_snapshot(&v);
        bool ign_likely = v.state == ST_DRIVE || v.vbat_mv > 13200 || config_ign_tap_active();
        if (!ign_likely || kl_foreign_activity() || now_ms() < backoff_until) {
            if (s_online) { s_online = false; VS_SET(src, vstate_raw()->src & ~SRC_ECU_ONLINE); }
            vTaskDelay(pdMS_TO_TICKS(500));
            continue;
        }
        if (!s_online) {
            esp_err_t e;
            if (s_prof.proto == 0) e = obd_run_probe();
            else {
                xSemaphoreTake(s_bus, portMAX_DELAY);
                e = try_connect(s_prof.proto, s_prof.target, s_prof.functional);
                xSemaphoreGive(s_bus);
            }
            if (e != ESP_OK) { backoff_until = now_ms() + (fails++ < 3 ? 5000 : 30000); continue; }
            s_online = true; fails = 0;
            vstate_lock(); vstate_raw()->src |= SRC_ECU_ONLINE; vstate_unlock();
        }

        uint32_t t = now_ms(); int best = -1; int32_t worst = 0;
        for (size_t i = 0; i < sizeof s_sched / sizeof s_sched[0]; i++) {
            if (!pid_supported(s_sched[i].pid)) continue;
            int32_t overdue = (int32_t)(t - s_sched[i].last) - s_sched[i].period_ms;
            if (overdue > worst || best < 0) { worst = overdue; best = i; }
        }
        uint8_t r[64]; size_t n; esp_err_t e = ESP_OK;
        if (best >= 0 && worst >= 0) {
            uint8_t q[2] = { 0x01, s_sched[best].pid };
            e = req(q, 2, r, &n);
            s_sched[best].last = t;
            if (e == ESP_OK && n >= 3 && r[1] == q[1]) decode_pid(q[1], r + 2, n - 2);
        } else if (config_lidmap(NULL) > 0 && t - last_lid > 250) {
            const lidmap_t *m; config_lidmap(&m);
            static int idx; uint8_t lid = m[idx++ % config_lidmap(NULL)].lid;
            uint8_t q[2] = { 0x21, lid };
            e = req(q, 2, r, &n);
            if (e == ESP_OK && n >= 2) apply_lid(lid, r + 2, n - 2);
            last_lid = t;
        } else if (t - kl_last_tx_ms() > 2000) {
            uint8_t q[2] = { 0x3E, 0x01 };
            if (s_prof.proto == KL_PROTO_ISO9141) { q[0] = 0x01; q[1] = 0x00; }
            e = req(q, 2, r, &n);
            if (e == ESP_ERR_TIMEOUT) e = ESP_OK;
        } else {
            vTaskDelay(pdMS_TO_TICKS(10));
        }
        if (e == ESP_ERR_TIMEOUT || e == ESP_ERR_INVALID_CRC || e == ESP_ERR_INVALID_RESPONSE) {
            if (++fails >= 3) {
                ESP_LOGW(TAG, "ECU lost");
                s_online = false; fails = 0;
                vstate_lock(); vstate_raw()->src &= ~(SRC_ECU_ONLINE | SRC_RPM_ECU | SRC_SPEED_ECU); vstate_unlock();
                backoff_until = now_ms() + 2000;
            }
        } else if (e == ESP_OK) fails = 0;
    }
}

void obd_start(void)
{
    s_bus = xSemaphoreCreateMutex();
    xTaskCreatePinnedToCore(vehicle_task, "vehicle", 6144, NULL, 10, NULL, 1);
}
