#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/uart.h"
#include "driver/gpio.h"
#include "esp_rom_gpio.h"
#include "soc/gpio_sig_map.h"
#include "esp_timer.h"
#include "esp_log.h"
#include "esp_check.h"
#include "board.h"
#include "kline.h"
#include "vguard.h"
#include "vstate.h"

static const char *TAG = "kline";

#define KL_BAUD        10400
#define P2_MAX_MS      50
#define P2_EXT_MS      5000
#define P1_MAX_MS      20
#define P3_MIN_MS      60
#define W5_MS          300
#define FOREIGN_HOLDOFF_MS (10 * 60 * 1000)

static uint32_t s_last_rx_ms, s_last_tx_ms, s_foreign_until;

bool     kl_foreign_activity(void) { return now_ms() < s_foreign_until; }
uint32_t kl_last_tx_ms(void) { return s_last_tx_ms; }

static inline void busy_wait_us(int64_t us)
{
    int64_t t_end = esp_timer_get_time() + us;
    while (esp_timer_get_time() < t_end) { }
}

static void tx_gpio_mode(void)
{
    gpio_set_direction(PIN_K_TX, GPIO_MODE_OUTPUT);
    esp_rom_gpio_connect_out_signal(PIN_K_TX, SIG_GPIO_OUT_IDX, false, false);
}
static inline void k_line(bool low) { gpio_set_level(PIN_K_TX, low ? 1 : 0); }
static void tx_uart_mode(void)
{
    uart_set_pin(K_UART, PIN_K_TX, PIN_K_RX, UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE);
    uart_set_line_inverse(K_UART, UART_SIGNAL_TXD_INV);
}

esp_err_t kl_init(void)
{
    const uart_config_t cfg = {
        .baud_rate = KL_BAUD, .data_bits = UART_DATA_8_BITS, .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1, .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };
    ESP_ERROR_CHECK(uart_driver_install(K_UART, 512, 0, 0, NULL, 0));
    ESP_ERROR_CHECK(uart_param_config(K_UART, &cfg));
    tx_uart_mode();
    gpio_set_pull_mode(PIN_K_RX, GPIO_FLOATING);
    return ESP_OK;
}

static bool wait_idle(uint32_t ms)
{
    uint8_t b;
    uint32_t t0 = now_ms();
    while (now_ms() - t0 < ms) {
        if (uart_read_bytes(K_UART, &b, 1, pdMS_TO_TICKS(5)) == 1) {
            s_foreign_until = now_ms() + FOREIGN_HOLDOFF_MS;
            t0 = now_ms();
            if (now_ms() - s_last_tx_ms > 2000) {
                ESP_LOGW(TAG, "foreign K-line traffic, holding off");
                return false;
            }
        }
    }
    return true;
}

static esp_err_t send_raw(const uint8_t *d, size_t n, uint8_t p4_ms)
{
    for (size_t i = 0; i < n; i++) {
        uart_write_bytes(K_UART, &d[i], 1);
        uint8_t echo;
        if (uart_read_bytes(K_UART, &echo, 1, pdMS_TO_TICKS(5)) != 1 || echo != d[i]) {
            ESP_LOGE(TAG, "echo mismatch at %u (bus fault or collision)", (unsigned)i);
            return ESP_ERR_INVALID_RESPONSE;
        }
        if (p4_ms && i + 1 < n) busy_wait_us(p4_ms * 1000);
    }
    s_last_tx_ms = now_ms();
    return ESP_OK;
}

static uint8_t checksum(const uint8_t *d, size_t n)
{
    uint8_t cs = 0;
    while (n--) cs += *d++;
    return cs;
}

static esp_err_t recv_msg(kl_session_t *s, uint8_t *msg, size_t max, size_t *out_len, uint32_t p2_ms)
{
    size_t n = 0, need;
    if (uart_read_bytes(K_UART, &msg[0], 1, pdMS_TO_TICKS(p2_ms)) != 1) return ESP_ERR_TIMEOUT;
    n = 1;
    if (s->proto == KL_PROTO_ISO9141) {
        uint8_t b;
        while (n < max && uart_read_bytes(K_UART, &b, 1, pdMS_TO_TICKS(P1_MAX_MS + 5)) == 1) msg[n++] = b;
        *out_len = n;
        if (n < 5 || checksum(msg, n - 1) != msg[n - 1]) return ESP_ERR_INVALID_CRC;
        return ESP_OK;
    }
    uint8_t fmt = msg[0];
    size_t hdr = (fmt & 0x80) ? 3 : 1;
    size_t len_field = (fmt & 0x3F) ? 0 : 1;
    need = hdr + len_field;
    while (n < need) {
        if (uart_read_bytes(K_UART, &msg[n], 1, pdMS_TO_TICKS(P1_MAX_MS + 5)) != 1) return ESP_ERR_TIMEOUT;
        n++;
    }
    size_t dlen = len_field ? msg[hdr] : (fmt & 0x3F);
    need = hdr + len_field + dlen + 1;
    if (need > max) return ESP_ERR_INVALID_SIZE;
    while (n < need) {
        if (uart_read_bytes(K_UART, &msg[n], 1, pdMS_TO_TICKS(P1_MAX_MS + 5)) != 1) return ESP_ERR_TIMEOUT;
        n++;
    }
    s_last_rx_ms = now_ms();
    *out_len = n;
    return checksum(msg, n - 1) == msg[n - 1] ? ESP_OK : ESP_ERR_INVALID_CRC;
}

static size_t data_offset(const uint8_t *msg, kl_proto_t p)
{
    if (p == KL_PROTO_ISO9141) return 3;
    size_t hdr = (msg[0] & 0x80) ? 3 : 1;
    return hdr + ((msg[0] & 0x3F) ? 0 : 1);
}

static esp_err_t fast_init(kl_session_t *s)
{
    if (!wait_idle(W5_MS)) return ESP_ERR_INVALID_STATE;
    uart_flush_input(K_UART);
    tx_gpio_mode();
    k_line(true);  busy_wait_us(25000);
    k_line(false); busy_wait_us(25000);
    tx_uart_mode();

    uint8_t req[5];
    req[0] = s->functional ? 0xC1 : 0x81;
    req[1] = s->target; req[2] = KL_TESTER_ADDR; req[3] = 0x81;
    req[4] = checksum(req, 4);
    ESP_RETURN_ON_ERROR(send_raw(req, 5, s->p4_ms), TAG, "startComm tx");
    uint8_t msg[KL_MAX_MSG]; size_t n;
    ESP_RETURN_ON_ERROR(recv_msg(s, msg, sizeof msg, &n, P2_MAX_MS + 20), TAG, "startComm rx");
    size_t o = data_offset(msg, s->proto);
    if (msg[o] != 0xC1) return ESP_ERR_INVALID_RESPONSE;
    s->kb1 = msg[o + 1]; s->kb2 = msg[o + 2];
    return ESP_OK;
}

static esp_err_t slow_init(kl_session_t *s)
{
    if (!wait_idle(W5_MS)) return ESP_ERR_INVALID_STATE;
    uart_flush_input(K_UART);
    tx_gpio_mode();
    uint8_t addr = s->target;
    k_line(true); vTaskDelay(pdMS_TO_TICKS(200));
    for (int i = 0; i < 8; i++) { k_line(!((addr >> i) & 1)); vTaskDelay(pdMS_TO_TICKS(200)); }
    k_line(false); vTaskDelay(pdMS_TO_TICKS(200));
    tx_uart_mode();
    uart_flush_input(K_UART);

    uint8_t b;
    if (uart_read_bytes(K_UART, &b, 1, pdMS_TO_TICKS(320)) != 1 || b != 0x55) return ESP_ERR_TIMEOUT;
    if (uart_read_bytes(K_UART, &s->kb1, 1, pdMS_TO_TICKS(25)) != 1) return ESP_ERR_TIMEOUT;
    if (uart_read_bytes(K_UART, &s->kb2, 1, pdMS_TO_TICKS(25)) != 1) return ESP_ERR_TIMEOUT;
    vTaskDelay(pdMS_TO_TICKS(30));
    uint8_t inv = ~s->kb2;
    ESP_RETURN_ON_ERROR(send_raw(&inv, 1, 0), TAG, "kb2 inv");
    if (uart_read_bytes(K_UART, &b, 1, pdMS_TO_TICKS(60)) != 1 || b != (uint8_t)~addr) return ESP_ERR_INVALID_RESPONSE;
    s_last_rx_ms = now_ms();
    return ESP_OK;
}

esp_err_t kl_connect(kl_session_t *s)
{
    if (kl_foreign_activity()) return ESP_ERR_INVALID_STATE;
    if (!s->p4_ms) s->p4_ms = 5;
    esp_err_t e = (s->proto == KL_PROTO_KWP_FAST) ? fast_init(s) : slow_init(s);
    if (e == ESP_OK) {
        ESP_LOGI(TAG, "connected proto=%d tgt=0x%02X KB=%02X %02X", s->proto, s->target, s->kb1, s->kb2);
        vTaskDelay(pdMS_TO_TICKS(P3_MIN_MS));
    }
    return e;
}

esp_err_t kl_request(kl_session_t *s, const uint8_t *req, size_t req_len,
                     uint8_t *resp, size_t resp_max, size_t *resp_len)
{
    if (req_len == 0 || req_len > 63) return ESP_ERR_INVALID_ARG;
    if (!vguard_allow(req, req_len)) return ESP_ERR_NOT_ALLOWED;
    if (kl_foreign_activity()) return ESP_ERR_INVALID_STATE;

    uint8_t msg[KL_MAX_MSG]; size_t n = 0;
    if (s->proto == KL_PROTO_ISO9141) {
        msg[n++] = 0x68; msg[n++] = 0x6A; msg[n++] = KL_TESTER_ADDR;
    } else {
        msg[n++] = (s->functional ? 0xC0 : 0x80) | (uint8_t)req_len;
        msg[n++] = s->target; msg[n++] = KL_TESTER_ADDR;
    }
    memcpy(msg + n, req, req_len); n += req_len;
    msg[n] = checksum(msg, n); n++;

    uint32_t since = now_ms() - s_last_rx_ms;
    if (since < P3_MIN_MS) vTaskDelay(pdMS_TO_TICKS(P3_MIN_MS - since));
    uart_flush_input(K_UART);
    ESP_RETURN_ON_ERROR(send_raw(msg, n, s->p4_ms), TAG, "tx");

    uint32_t p2 = P2_MAX_MS + 20;
    for (int tries = 0; tries < 20; tries++) {
        uint8_t rx[KL_MAX_MSG]; size_t rn;
        esp_err_t e = recv_msg(s, rx, sizeof rx, &rn, p2);
        if (e != ESP_OK) return e;
        size_t o = data_offset(rx, s->proto);
        size_t dl = rn - o - 1;
        if (dl >= 3 && rx[o] == 0x7F && rx[o + 1] == req[0] && rx[o + 2] == 0x78) {
            p2 = P2_EXT_MS;
            continue;
        }
        if (dl > resp_max) return ESP_ERR_INVALID_SIZE;
        memcpy(resp, rx + o, dl);
        *resp_len = dl;
        if (resp[0] == 0x7F) return ESP_ERR_NOT_SUPPORTED;
        if (resp[0] != (uint8_t)(req[0] + 0x40)) continue;
        return ESP_OK;
    }
    return ESP_ERR_TIMEOUT;
}

esp_err_t kl_read_next(kl_session_t *s, uint8_t *resp, size_t resp_max, size_t *resp_len, uint32_t timeout_ms)
{
    uint8_t rx[KL_MAX_MSG]; size_t rn;
    esp_err_t e = recv_msg(s, rx, sizeof rx, &rn, timeout_ms);
    if (e != ESP_OK) return e;
    size_t o = data_offset(rx, s->proto), dl = rn - o - 1;
    if (dl > resp_max) return ESP_ERR_INVALID_SIZE;
    memcpy(resp, rx + o, dl);
    *resp_len = dl;
    return ESP_OK;
}

void kl_disconnect(kl_session_t *s)
{
    if (s->proto == KL_PROTO_KWP_FAST || s->proto == KL_PROTO_KWP_5BAUD) {
        const uint8_t stop = 0x82;
        uint8_t r[8]; size_t rl;
        kl_request(s, &stop, 1, r, sizeof r, &rl);
    }
    s->proto = KL_PROTO_NONE;
}
