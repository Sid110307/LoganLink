#include <string.h>
#include <time.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/uart.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "board.h"
#include "gnss.h"
#include "vstate.h"
#include "proto.h"

static const char *TAG = "gnss";
static TaskHandle_t s_task;
static volatile bool s_run;
static volatile uint16_t s_rate_ms = 200;
static gnss_fix_t s_last_good;

const gnss_fix_t *gnss_last_good_fix(void) { return &s_last_good; }

static void ubx_send(uint8_t cls, uint8_t id, const uint8_t *pl, uint16_t len)
{
    uint8_t hdr[6] = { 0xB5, 0x62, cls, id, (uint8_t)len, (uint8_t)(len >> 8) };
    uint8_t ca = 0, cb = 0;
    for (int i = 2; i < 6; i++) { ca += hdr[i]; cb += ca; }
    for (int i = 0; i < len; i++) { ca += pl[i]; cb += ca; }
    uint8_t ck[2] = { ca, cb };
    uart_write_bytes(GNSS_UART, hdr, 6);
    uart_write_bytes(GNSS_UART, pl, len);
    uart_write_bytes(GNSS_UART, ck, 2);
}

static void configure(uint16_t meas_ms)
{
    uint8_t p[64]; int n = 0;
    p[n++] = 0x00; p[n++] = 0x01; p[n++] = 0; p[n++] = 0;
#define KEY(k) do { put_u32(p + n, (k)); n += 4; } while (0)
    KEY(0x30210001); put_u16(p + n, meas_ms); n += 2;
    KEY(0x20910007); p[n++] = 1;
    KEY(0x10740002); p[n++] = 0;
    KEY(0x20110021); p[n++] = 4;
    KEY(0x10310021); p[n++] = 1;
    KEY(0x10310022); p[n++] = 1;
    KEY(0x10310025); p[n++] = 1;
#undef KEY
    ubx_send(0x06, 0x8A, p, n);
}

static void handle_pvt(const uint8_t *p)
{
    uint8_t valid = p[11], fix = p[20], flags = p[21], nsv = p[23];
    int32_t lon = (int32_t)get_u32(p + 24), lat = (int32_t)get_u32(p + 28);
    int32_t gspeed = (int32_t)get_u32(p + 60);
    int32_t head = (int32_t)get_u32(p + 64);
    uint16_t pdop = get_u16(p + 76);
    bool ok = (flags & 1) && fix >= 2;
    vstate_lock();
    vstate_t *v = vstate_raw();
    v->sats = nsv; v->fix = ok ? fix : 0;
    if (ok) {
        v->lat_e7 = lat; v->lon_e7 = lon;
        v->gspeed_x10 = (uint16_t)(gspeed * 36 / 1000);
        v->heading_x10 = (uint16_t)(head / 10000);
        v->hdop = pdop / 100.f;
        v->src |= SRC_GNSS_VALID;
        if (now_ms() - v->ecu_speed_ms > 1000) {
            v->speed_x10 = v->gspeed_x10;
            v->src = (v->src | SRC_SPEED_GNSS) & ~SRC_SPEED_ECU;
        }
    } else {
        v->src &= ~SRC_GNSS_VALID;
    }
    if ((valid & 0x03) == 0x03) {
        struct tm t = { .tm_year = get_u16(p + 4) - 1900, .tm_mon = p[6] - 1, .tm_mday = p[7],
                        .tm_hour = p[8], .tm_min = p[9], .tm_sec = p[10] };
        v->utc = (uint32_t)mktime(&t);
    }
    vstate_unlock();
    if (ok && pdop < 250) {
        s_last_good.lat_e7 = lat; s_last_good.lon_e7 = lon;
        s_last_good.utc = vstate_raw()->utc; s_last_good.pdop_x100 = pdop;
    }
}

static void gnss_task(void *arg)
{
    uint8_t b, st = 0, cls = 0, id = 0, ca = 0, cb = 0;
    uint16_t len = 0, idx = 0;
    static uint8_t pl[100];
    configure(s_rate_ms);
    uint16_t cfg_rate = s_rate_ms;
    while (s_run) {
        if (cfg_rate != s_rate_ms) { cfg_rate = s_rate_ms; configure(cfg_rate); }
        if (uart_read_bytes(GNSS_UART, &b, 1, pdMS_TO_TICKS(100)) != 1) continue;
        switch (st) {
        case 0: st = (b == 0xB5); break;
        case 1: st = (b == 0x62) ? 2 : 0; break;
        case 2: cls = b; ca = cb = 0; ca += b; cb += ca; st = 3; break;
        case 3: id = b; ca += b; cb += ca; st = 4; break;
        case 4: len = b; ca += b; cb += ca; st = 5; break;
        case 5: len |= b << 8; ca += b; cb += ca; idx = 0; st = (len <= sizeof pl) ? 6 : 0; if (len == 0) st = 7; break;
        case 6: pl[idx++] = b; ca += b; cb += ca; if (idx == len) st = 7; break;
        case 7: st = (b == ca) ? 8 : 0; break;
        case 8:
            if (b == cb && cls == 0x01 && id == 0x07 && len == 92) handle_pvt(pl);
            st = 0; break;
        }
    }
    s_task = NULL;
    vTaskDelete(NULL);
}

void gnss_start(uint16_t rate_ms)
{
    s_rate_ms = rate_ms;
    if (s_task) return;
    gpio_set_level(PIN_GNSS_EN, 1);
    vTaskDelay(pdMS_TO_TICKS(50));
    const uart_config_t c = { .baud_rate = 38400, .data_bits = UART_DATA_8_BITS, .parity = UART_PARITY_DISABLE,
                              .stop_bits = UART_STOP_BITS_1, .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
                              .source_clk = UART_SCLK_DEFAULT };
    if (!uart_is_driver_installed(GNSS_UART)) uart_driver_install(GNSS_UART, 1024, 0, 0, NULL, 0);
    uart_param_config(GNSS_UART, &c);
    uart_set_pin(GNSS_UART, PIN_GNSS_TX, PIN_GNSS_RX, UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
    s_run = true;
    xTaskCreatePinnedToCore(gnss_task, "gnss", 4096, NULL, 8, &s_task, 1);
    ESP_LOGI(TAG, "started, %u ms", rate_ms);
}

void gnss_set_rate(uint16_t rate_ms) { s_rate_ms = rate_ms; }

void gnss_stop(void)
{
    s_run = false;
    while (s_task) vTaskDelay(pdMS_TO_TICKS(10));
    gpio_set_level(PIN_GNSS_EN, 0);
}
