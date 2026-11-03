#include <math.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_adc/adc_continuous.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "esp_log.h"
#include "esp_dsp.h"
#include "board.h"
#include "vbat.h"
#include "vstate.h"
#include "config.h"
#include "logger.h"

static const char *TAG = "vbat";

#define FS_PER_CH      40000
#define FFT_N          2048
#define FRAME_BYTES    (256 * SOC_ADC_DIGI_RESULT_BYTES)

static adc_continuous_handle_t s_adc;
static adc_cali_handle_t s_cali;
static TaskHandle_t s_task;
static volatile bool s_run;
static float s_ac[FFT_N] __attribute__((aligned(16)));
static float s_fft[FFT_N * 2] __attribute__((aligned(16)));
static float s_win[FFT_N];
static int   s_ac_n;
static volatile uint16_t s_mv_fast, s_ripple_mvpp;
static volatile float s_ripple_hz;
static volatile charge_state_t s_chg;

charge_state_t vbat_charge_state(void) { return s_chg; }
uint16_t vbat_ripple_mvpp(void) { return s_ripple_mvpp; }
float    vbat_ripple_freq_hz(void) { return s_ripple_hz; }
uint16_t vbat_mv_fast(void) { return s_mv_fast; }

static inline uint32_t raw_to_vbat_mv(int raw)
{
    int pin_mv = 0;
    adc_cali_raw_to_voltage(s_cali, raw, &pin_mv);
    int64_t mv = (int64_t)pin_mv * VBAT_DIV_NUM / VBAT_DIV_DEN;
    mv = mv * config_get(CFG_VBAT_GAIN_PPM) / 1000000 + config_get(CFG_VBAT_OFFSET_MV);
    return mv < 0 ? 0 : (uint32_t)mv;
}

static void analyse_ripple(void)
{
    float mn = 1e9f, mx = -1e9f, mean = 0;
    for (int i = 0; i < FFT_N; i++) { mean += s_ac[i]; if (s_ac[i] < mn) mn = s_ac[i]; if (s_ac[i] > mx) mx = s_ac[i]; }
    mean /= FFT_N;
    s_ripple_mvpp = (uint16_t)(mx - mn);

    for (int i = 0; i < FFT_N; i++) { s_fft[2 * i] = (s_ac[i] - mean) * s_win[i]; s_fft[2 * i + 1] = 0; }
    dsps_fft2r_fc32(s_fft, FFT_N);
    dsps_bit_rev_fc32(s_fft, FFT_N);
    const float bin_hz = (float)FS_PER_CH / FFT_N;
    int lo = (int)(1000 / bin_hz), hi = (int)(10500 / bin_hz);
    int pk = lo; float pmax = 0;
    for (int k = lo; k <= hi; k++) {
        float m = s_fft[2 * k] * s_fft[2 * k] + s_fft[2 * k + 1] * s_fft[2 * k + 1];
        if (m > pmax) { pmax = m; pk = k; }
    }
    float a = logf(1e-9f + s_fft[2*(pk-1)]*s_fft[2*(pk-1)] + s_fft[2*(pk-1)+1]*s_fft[2*(pk-1)+1]);
    float b = logf(1e-9f + pmax);
    float c = logf(1e-9f + s_fft[2*(pk+1)]*s_fft[2*(pk+1)] + s_fft[2*(pk+1)+1]*s_fft[2*(pk+1)+1]);
    float delta = 0.5f * (a - c) / (a - 2 * b + c);
    float f = (pk + delta) * bin_hz;

    bool charging = s_mv_fast > 13200 && s_ripple_mvpp > 20;
    s_ripple_hz = charging ? f : 0;

    vstate_t v; vstate_snapshot(&v);
    int32_t krip = config_get(CFG_KRIP_MILLI);
    bool ecu_rpm_fresh = (v.src & SRC_RPM_ECU) && now_ms() - v.ecu_rpm_ms < 1000;
    if (charging && ecu_rpm_fresh && v.rpm > 700 && v.rpm < 4500) {
        static float k_est; static int n;
        float k = f / v.rpm;
        k_est = (n == 0) ? k : 0.98f * k_est + 0.02f * k;
        if (++n % 600 == 0) config_set(CFG_KRIP_MILLI, (int32_t)(k_est * 1000));
    } else if (charging && !ecu_rpm_fresh && krip > 0) {
        uint16_t rpm = (uint16_t)(f * 1000.f / krip);
        vstate_lock();
        vstate_raw()->rpm = rpm;
        vstate_raw()->src = (vstate_raw()->src | SRC_RPM_RIPPLE) & ~SRC_RPM_ECU;
        vstate_unlock();
    }
}

typedef enum { CR_IDLE, CR_ACTIVE } crank_phase_t;

static void crank_update(uint32_t mv, uint32_t t_ms)
{
    static float rest = 12600; static crank_phase_t ph = CR_IDLE;
    static crank_rec_t rec; static uint32_t t_start, t_min, t_end;
    if (ph == CR_IDLE) {
        if (mv < 13000 && mv > 11000) rest = 0.999f * rest + 0.001f * mv;
        if (mv + 1500 < rest) {
            ph = CR_ACTIVE; memset(&rec, 0, sizeof rec);
            rec.rest_mv = (uint16_t)rest; rec.min_mv = mv; t_start = t_min = t_ms; t_end = 0;
        }
    } else {
        if (mv < rec.min_mv) { rec.min_mv = mv; t_min = t_ms; }
        if (!t_end && mv + 1000 > rest) t_end = t_ms;
        if (t_end && mv > 13000) {
            rec.crank_ms = t_end - t_start; rec.recover_ms = t_ms - t_min;
            logger_write(REC_CRANK, &rec, sizeof rec);
            ESP_LOGI(TAG, "crank: rest %u min %u dur %u ms", rec.rest_mv, rec.min_mv, rec.crank_ms);
            ph = CR_IDLE;
        } else if (t_ms - t_start > 15000) {
            rec.crank_ms = (t_end ? t_end : t_ms) - t_start; rec.recover_ms = 0xFFFF;
            logger_write(REC_CRANK, &rec, sizeof rec);
            ph = CR_IDLE;
        }
    }
}

static void vbat_task(void *arg)
{
    uint8_t buf[FRAME_BYTES];
    uint32_t dc_acc = 0, dc_n = 0, dec_n = 0;
    uint32_t last_pub = 0, last_fft = 0;
    float filt = 0;
    while (s_run) {
        uint32_t got = 0;
        if (adc_continuous_read(s_adc, buf, sizeof buf, &got, 100) != ESP_OK) continue;
        for (uint32_t i = 0; i < got; i += SOC_ADC_DIGI_RESULT_BYTES) {
            adc_digi_output_data_t *p = (adc_digi_output_data_t *)&buf[i];
            int ch = p->type2.channel, raw = p->type2.data;
            if (ch == ADC_CH_VBAT_DC) {
                dc_acc += raw; dc_n++;
                if (++dec_n == FS_PER_CH / 1000) {
                    uint32_t mv = raw_to_vbat_mv(dc_acc / dc_n);
                    crank_update(mv, now_ms());
                    filt = filt ? 0.9f * filt + 0.1f * mv : mv;
                    s_mv_fast = (uint16_t)filt;
                    dc_acc = dc_n = dec_n = 0;
                }
            } else if (ch == ADC_CH_VBAT_AC && s_ac_n < FFT_N && now_ms() - last_fft > 1000) {
                int mv = 0; adc_cali_raw_to_voltage(s_cali, raw, &mv);
                s_ac[s_ac_n++] = mv;
            }
        }
        if (s_ac_n == FFT_N) { analyse_ripple(); s_ac_n = 0; last_fft = now_ms(); }
        uint32_t t = now_ms();
        if (t - last_pub >= 100) {
            last_pub = t;
            VS_SET(vbat_mv, s_mv_fast);
            uint16_t mv = s_mv_fast;
            s_chg = mv < 13000 ? CHG_NOT_CHARGING : mv < 13500 ? CHG_LOW : mv <= 14900 ? CHG_NORMAL : CHG_OVER;
        }
    }
    s_task = NULL;
    vTaskDelete(NULL);
}

void vbat_start(void)
{
    if (!s_cali) {
        adc_cali_curve_fitting_config_t cc = { .unit_id = ADC_UNIT_1, .atten = ADC_ATTEN_DB_12, .bitwidth = ADC_BITWIDTH_12 };
        ESP_ERROR_CHECK(adc_cali_create_scheme_curve_fitting(&cc, &s_cali));
        dsps_fft2r_init_fc32(NULL, FFT_N);
        dsps_wind_hann_f32(s_win, FFT_N);
    }
    adc_continuous_handle_cfg_t hc = { .max_store_buf_size = 8 * FRAME_BYTES, .conv_frame_size = FRAME_BYTES };
    ESP_ERROR_CHECK(adc_continuous_new_handle(&hc, &s_adc));
    adc_digi_pattern_config_t pat[2] = {
        { .atten = ADC_ATTEN_DB_12, .channel = ADC_CH_VBAT_DC, .unit = ADC_UNIT_1, .bit_width = ADC_BITWIDTH_12 },
        { .atten = ADC_ATTEN_DB_12, .channel = ADC_CH_VBAT_AC, .unit = ADC_UNIT_1, .bit_width = ADC_BITWIDTH_12 },
    };
    adc_continuous_config_t dc = {
        .pattern_num = 2, .adc_pattern = pat, .sample_freq_hz = 2 * FS_PER_CH,
        .conv_mode = ADC_CONV_SINGLE_UNIT_1, .format = ADC_DIGI_OUTPUT_FORMAT_TYPE2,
    };
    ESP_ERROR_CHECK(adc_continuous_config(s_adc, &dc));
    ESP_ERROR_CHECK(adc_continuous_start(s_adc));
    s_run = true;
    xTaskCreatePinnedToCore(vbat_task, "vbat", 6144, NULL, 12, &s_task, 1);
}

void vbat_stop(void)
{
    s_run = false;
    while (s_task) vTaskDelay(pdMS_TO_TICKS(10));
    adc_continuous_stop(s_adc);
    adc_continuous_deinit(s_adc);
    s_adc = NULL;
}
