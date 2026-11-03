#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "driver/rtc_io.h"
#include "driver/temperature_sensor.h"
#include "esp_sleep.h"
#include "esp_log.h"
#include "esp_system.h"
#include "host/ble_store.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "ulp_riscv.h"
#include "ulp_adc.h"
#include "ulp_main.h"
#include "board.h"
#include "power.h"
#include "vstate.h"
#include "vbat.h"
#include "sensors.h"
#include "ble_link.h"
#include "trip.h"
#include "fuel.h"
#include "logger.h"
#include "config.h"
#include "obd.h"

static const char *TAG = "power";
extern const uint8_t ulp_main_bin_start[] asm("_binary_ulp_main_bin_start");
extern const uint8_t ulp_main_bin_end[]   asm("_binary_ulp_main_bin_end");

#define STANDBY_TO_SLEEP_MS  (5 * 60 * 1000)
#define PARKED_HIBERNATE_S   (14 * 24 * 3600)

static uint8_t s_state = ST_BOOT;
static RTC_DATA_ATTR uint32_t s_parked_since_utc;
static RTC_DATA_ATTR uint32_t s_sleep_count;
static uint32_t s_standby_timeout_ms = STANDBY_TO_SLEEP_MS;

uint8_t power_state(void) { return s_state; }

static void led(bool r, bool g) { gpio_set_level(PIN_LED_R, r); gpio_set_level(PIN_LED_G, g); }

static uint32_t mv_to_raw(uint32_t vbat_mv)
{
    adc_cali_handle_t cal;
    adc_cali_curve_fitting_config_t cc = { .unit_id = ADC_UNIT_1, .atten = ADC_ATTEN_DB_12, .bitwidth = ADC_BITWIDTH_12 };
    if (adc_cali_create_scheme_curve_fitting(&cc, &cal) != ESP_OK) return 4095;
    int64_t pin_mv = (int64_t)vbat_mv * VBAT_DIV_DEN / VBAT_DIV_NUM;
    pin_mv = ((int64_t)vbat_mv - config_get(CFG_VBAT_OFFSET_MV)) * 1000000 / config_get(CFG_VBAT_GAIN_PPM) * VBAT_DIV_DEN / VBAT_DIV_NUM;
    uint32_t raw = 0;
    for (; raw < 4095; raw += 4) { int mv; adc_cali_raw_to_voltage(cal, raw, &mv); if (mv >= pin_mv) break; }
    adc_cali_delete_scheme_curve_fitting(cal);
    return raw;
}

static void enter_sleep(bool hibernate)
{
    ESP_LOGI(TAG, "entering %s", hibernate ? "HIBERNATE" : "SLEEP");
    s_state = ST_SLEEP; VS_SET(state, ST_SLEEP);
    logger_write(REC_EVENT, (uint8_t[]){ hibernate ? 21 : 20 }, 1);
    ble_link_set_advertising(false);
    sensors_stop(); sensors_power(false);
    vbat_stop();
    led(false, false);
    if (!s_parked_since_utc) { vstate_t v; vstate_snapshot(&v); s_parked_since_utc = v.utc; }

    ulp_adc_cfg_t ac = { .adc_n = ADC_UNIT_1, .channel = ADC_CH_VBAT_DC, .width = ADC_BITWIDTH_DEFAULT,
                         .atten = ADC_ATTEN_DB_12, .ulp_mode = ADC_ULP_MODE_RISCV };
    ESP_ERROR_CHECK(ulp_adc_init(&ac));
    ESP_ERROR_CHECK(ulp_riscv_load_binary(ulp_main_bin_start, ulp_main_bin_end - ulp_main_bin_start));
    ulp_thr_charge = mv_to_raw(hibernate ? 13000 : 13200);
    ulp_thr_step   = mv_to_raw(12600 + 150) - mv_to_raw(12600);
    ulp_step_enable = !hibernate;
    ulp_baseline_q8 = 0; ulp_wake_reason = 0;
    ulp_set_wakeup_period(0, hibernate ? 10000000 : 1000000);
    ESP_ERROR_CHECK(ulp_riscv_run());
    esp_sleep_enable_ulp_wakeup();

    if (!hibernate) {
        wake_accel_arm(120);
        rtc_gpio_pulldown_en(PIN_ACC_WAKE_INT);
        esp_sleep_enable_ext0_wakeup(PIN_ACC_WAKE_INT, 1);
    }
    if (config_get(CFG_IGN_TAP_FITTED)) {
        rtc_gpio_pullup_dis(PIN_IGN_SENSE_N);
        esp_sleep_enable_ext1_wakeup(1ULL << PIN_IGN_SENSE_N, ESP_EXT1_WAKEUP_ANY_LOW);
    }
    esp_sleep_enable_timer_wakeup(6ULL * 3600 * 1000000);
    gpio_hold_en(PIN_AUX_PWR); gpio_hold_en(PIN_LED_R); gpio_hold_en(PIN_LED_G);
    gpio_deep_sleep_hold_en();
    s_sleep_count++;
    esp_deep_sleep_start();
}

static void apply_state(uint8_t st)
{
    if (st == s_state) return;
    ESP_LOGI(TAG, "state %d -> %d", s_state, st);
    s_state = st; VS_SET(state, st);
    switch (st) {
    case ST_DRIVE:   s_parked_since_utc = 0; break;
    }
}

static void power_task(void *arg)
{
    temperature_sensor_handle_t ts = NULL;
    temperature_sensor_config_t tc = TEMPERATURE_SENSOR_CONFIG_DEFAULT(-10, 110);
    if (temperature_sensor_install(&tc, &ts) == ESP_OK) temperature_sensor_enable(ts);

    uint32_t last = now_ms(), standby_since = now_ms(), charge_since = 0, lowbat_since = 0, btn_since = 0, last_temp = 0;
    apply_state(ST_STANDBY);

    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(100));
        uint32_t t = now_ms(), dt = t - last; last = t;
        uint32_t utc_now;
        if (rtc_get_utc(&utc_now) == ESP_OK) VS_SET(utc, utc_now);
        vstate_t v; vstate_snapshot(&v);

        bool rpm_ok = v.rpm != 0xFFFF && v.rpm > 400 && (v.src & (SRC_RPM_ECU | SRC_RPM_RIPPLE));
        if (v.vbat_mv > 13200) { if (!charge_since) charge_since = t; } else charge_since = 0;
        bool running = rpm_ok || (charge_since && t - charge_since > 3000) || config_ign_tap_active();

        trip_tick(dt, running);
        if (running) fuel_update(dt);

        if (running) { apply_state(ST_DRIVE); standby_since = t; s_standby_timeout_ms = STANDBY_TO_SLEEP_MS; }
        else if (s_state == ST_DRIVE && !trip_active()) apply_state(ST_STANDBY);

        if (!running && v.vbat_mv && v.vbat_mv < config_get(CFG_LOWBAT_HIBERNATE_MV)) {
            if (!lowbat_since) lowbat_since = t;
            if (t - lowbat_since > 10 * 60 * 1000) enter_sleep(true);
        } else lowbat_since = 0;

        bool busy = ble_link_any_connected() || trip_active() || running;
        if (busy) standby_since = (running ? t : standby_since);
        if (!busy && t - standby_since > s_standby_timeout_ms) {
            bool long_parked = s_parked_since_utc && v.utc > s_parked_since_utc + PARKED_HIBERNATE_S;
            enter_sleep(long_parked);
        }

        if (gpio_get_level(PIN_BTN_PAIR) == 0) {
            if (!btn_since) btn_since = t;
            if (t - btn_since > 10000 && running) {
                ESP_LOGW(TAG, "factory reset");
                ble_store_clear();
                for (uint8_t u = 1; u < 8; u++) config_unrevoke_user(u);
                esp_restart();
            }
        } else if (btn_since) {
            if (t - btn_since > 50 && t - btn_since < 3000 && running) ble_link_open_pairing_window(120);
            btn_since = 0;
        }

        if (ts && t - last_temp > 10000) {
            last_temp = t; float tc_die = 0;
            temperature_sensor_get_celsius(ts, &tc_die);
            if (tc_die > 105) sensors_stop();
        }

        bool blink = (t / 500) & 1;
        if (s_state == ST_DRIVE) led(false, true);
        else led(false, blink && (t % 3000) < 100);
    }
}

void power_start(esp_sleep_wakeup_cause_t cause)
{
    if (cause == ESP_SLEEP_WAKEUP_ULP) ESP_LOGI(TAG, "wake: ULP reason %lu raw %lu", (unsigned long)ulp_wake_reason, (unsigned long)ulp_last_raw);
    else if (cause == ESP_SLEEP_WAKEUP_TIMER) {
        uint32_t raw = ulp_last_raw;
        logger_write(REC_EVENT, (uint8_t[]){ 22, (uint8_t)(raw >> 8), (uint8_t)raw }, 3);
    }
    if (cause == ESP_SLEEP_WAKEUP_TIMER) s_standby_timeout_ms = 10 * 1000;
    else if (cause == ESP_SLEEP_WAKEUP_EXT0 || (cause == ESP_SLEEP_WAKEUP_ULP && ulp_wake_reason == 2))
        s_standby_timeout_ms = 3 * 60 * 1000;
    gpio_hold_dis(PIN_AUX_PWR); gpio_hold_dis(PIN_LED_R); gpio_hold_dis(PIN_LED_G);
    gpio_deep_sleep_hold_dis();
    xTaskCreatePinnedToCore(power_task, "power", 4096, NULL, 11, NULL, 1);
}
