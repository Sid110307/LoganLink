#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_sleep.h"
#include "esp_ota_ops.h"
#include "esp_system.h"
#include "host/ble_hs.h"
#include "board.h"
#include "vstate.h"
#include "config.h"
#include "logger.h"
#include "sensors.h"
#include "vbat.h"
#include "kline.h"
#include "obd.h"
#include "ble_link.h"
#include "power.h"

static const char *TAG = "main";

static void gpio_setup(void)
{
    const gpio_config_t out = {
        .pin_bit_mask = (1ULL << PIN_LED_R) | (1ULL << PIN_LED_G) | (1ULL << PIN_AUX_PWR),
        .mode = GPIO_MODE_OUTPUT,
    };
    gpio_config(&out);
    const gpio_config_t in = {
        .pin_bit_mask = (1ULL << PIN_BTN_PAIR) | (1ULL << PIN_IGN_SENSE_N) | (1ULL << PIN_ACC_WAKE_INT),
        .mode = GPIO_MODE_INPUT,
    };
    gpio_config(&in);
}

static void ota_confirm_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(60000));
    bool ok = logger_head_seq() > 0 && ble_hs_synced();
    if (ok) {
        esp_ota_mark_app_valid_cancel_rollback();
        ESP_LOGI(TAG, "new firmware confirmed");
    } else {
        esp_ota_mark_app_invalid_rollback_and_reboot();
    }
    vTaskDelete(NULL);
}

void app_main(void)
{
    esp_sleep_wakeup_cause_t cause = esp_sleep_get_wakeup_cause();
    ESP_LOGI(TAG, "LoganLink %d.%d.%d hw %c, wake cause %d, reset reason %d",
             FW_VERSION_MAJOR, FW_VERSION_MINOR, FW_VERSION_PATCH, HW_REVISION, cause, esp_reset_reason());

    gpio_setup();
    vstate_init();
    ESP_ERROR_CHECK(config_init());

    sensors_init_aon();
    uint32_t utc;
    if (rtc_get_utc(&utc) == ESP_OK) VS_SET(utc, utc);
    ESP_ERROR_CHECK(sensors_power(true));
    bool safe = logger_init() != ESP_OK;
    uint8_t boot[4] = { (uint8_t)esp_reset_reason(), (uint8_t)cause, FW_VERSION_MAJOR, FW_VERSION_MINOR };
    logger_write(REC_BOOT, boot, sizeof boot);

    vbat_start();
    sensors_start();

    kl_init();
    if (!safe) obd_start();

    ble_link_start();

    VS_SET(state, safe ? ST_SAFE : ST_STANDBY);
    power_start(cause);

    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t st;
    if (esp_ota_get_state_partition(running, &st) == ESP_OK && st == ESP_OTA_IMG_PENDING_VERIFY)
        xTaskCreate(ota_confirm_task, "otaok", 3072, NULL, 5, NULL);
}
