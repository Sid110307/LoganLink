#include <string.h>
#include <math.h>
#include <sys/time.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/i2c_master.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_check.h"
#include "board.h"
#include "sensors.h"
#include "vstate.h"
#include "logger.h"

static const char *TAG = "sensors";
static i2c_master_bus_handle_t s_bus;
static i2c_master_dev_handle_t d_acc;
static TaskHandle_t s_task;
static volatile bool s_run;
static bool s_acc_ok;

static esp_err_t wr8(uint8_t reg, uint8_t v) { uint8_t b[2] = { reg, v }; return i2c_master_transmit(d_acc, b, 2, 20); }
static esp_err_t rd(uint8_t reg, uint8_t *o, size_t n) { return i2c_master_transmit_receive(d_acc, &reg, 1, o, n, 20); }

esp_err_t sensors_init_aon(void)
{
    i2c_master_bus_config_t bc = { .i2c_port = I2C_PORT, .sda_io_num = PIN_I2C_SDA, .scl_io_num = PIN_I2C_SCL,
                                   .clk_source = I2C_CLK_SRC_DEFAULT, .glitch_ignore_cnt = 7 };
    ESP_RETURN_ON_ERROR(i2c_new_master_bus(&bc, &s_bus), TAG, "i2c bus");
    i2c_device_config_t c = { .dev_addr_length = I2C_ADDR_BIT_LEN_7, .device_address = LIS2DW12_ADDR, .scl_speed_hz = 400000 };
    ESP_RETURN_ON_ERROR(i2c_master_bus_add_device(s_bus, &c, &d_acc), TAG, "acc dev");
    uint8_t who = 0;
    s_acc_ok = rd(0x0F, &who, 1) == ESP_OK && who == 0x44;
    if (!s_acc_ok) ESP_LOGW(TAG, "LIS2DW12 not found (0x%02X)", who);
    return ESP_OK;
}

esp_err_t sensors_power(bool on)
{
    gpio_set_level(PIN_AUX_PWR, on);
    if (on) vTaskDelay(pdMS_TO_TICKS(2));
    return ESP_OK;
}

esp_err_t wake_accel_arm(uint16_t threshold_mg)
{
    if (!s_acc_ok) return ESP_ERR_INVALID_STATE;
    uint8_t ths = (uint8_t)fminf(63, threshold_mg / 31.25f);
    ESP_RETURN_ON_ERROR(wr8(0x20, 0x20), TAG, "CTRL1");
    ESP_RETURN_ON_ERROR(wr8(0x25, 0x00), TAG, "CTRL6");
    ESP_RETURN_ON_ERROR(wr8(0x34, ths), TAG, "WAKE_UP_THS");
    ESP_RETURN_ON_ERROR(wr8(0x35, 0x00), TAG, "WAKE_UP_DUR");
    ESP_RETURN_ON_ERROR(wr8(0x23, 0x20), TAG, "CTRL4_INT1");
    ESP_RETURN_ON_ERROR(wr8(0x3F, 0x20), TAG, "CTRL7");
    return ESP_OK;
}

esp_err_t rtc_get_utc(uint32_t *utc)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    *utc = (uint32_t)tv.tv_sec;
    return *utc > 1700000000 ? ESP_OK : ESP_ERR_INVALID_STATE;
}

esp_err_t rtc_set_utc(uint32_t utc)
{
    struct timeval tv = { .tv_sec = utc, .tv_usec = 0 };
    return settimeofday(&tv, NULL) == 0 ? ESP_OK : ESP_FAIL;
}

static void acc_active(void)
{
    wr8(0x3F, 0x00);
    wr8(0x23, 0x00);
    wr8(0x21, 0x04);
    wr8(0x25, 0x30);
    wr8(0x20, 0x54);
}

static void acc_poll(void)
{
    uint8_t b[6];
    if (rd(0x28, b, 6) != ESP_OK) return;
    const float mg = 0.488f;
    float ax = (int16_t)(b[0] | b[1] << 8) * mg, ay = (int16_t)(b[2] | b[3] << 8) * mg, az = (int16_t)(b[4] | b[5] << 8) * mg;
    static float gx, gy, gz; static bool init;
    if (!init) { gx = ax; gy = ay; gz = az; init = true; }
    gx += 0.002f * (ax - gx); gy += 0.002f * (ay - gy); gz += 0.002f * (az - gz);
    float lx = ax - gx, ly = ay - gy, lz = az - gz;
    vstate_lock(); vstate_raw()->ax_mg = (int16_t)lx; vstate_raw()->ay_mg = (int16_t)ly; vstate_unlock();

    float mag = sqrtf(lx * lx + ly * ly + lz * lz);
    static uint32_t over_since; static bool latched;
    if (mag > 4000) {
        if (!over_since) over_since = now_ms();
        else if (!latched && now_ms() - over_since > 20) {
            int16_t ev[4] = { 1 , (int16_t)lx, (int16_t)ly, (int16_t)lz };
            logger_write(REC_EVENT, ev, sizeof ev);
            latched = true;
        }
    } else { over_since = 0; latched = false; }
}

static void sensors_task(void *arg)
{
    if (s_acc_ok) acc_active();
    while (s_run) {
        if (s_acc_ok) acc_poll();
        vTaskDelay(pdMS_TO_TICKS(10));
    }
    s_task = NULL;
    vTaskDelete(NULL);
}

void sensors_start(void)
{
    if (s_task) return;
    s_run = true;
    xTaskCreatePinnedToCore(sensors_task, "sensors", 3072, NULL, 7, &s_task, 1);
}

void sensors_stop(void)
{
    s_run = false;
    while (s_task) vTaskDelay(pdMS_TO_TICKS(10));
}
