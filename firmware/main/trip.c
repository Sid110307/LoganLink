#include <string.h>
#include "esp_log.h"
#include "board.h"
#include "trip.h"
#include "vstate.h"
#include "logger.h"
#include "fuel.h"
#include "config.h"
#include "proto.h"

static const char *TAG = "trip";
static bool s_active;
static trip_summary_t s_t;
static uint32_t s_off_since, s_last_sample;
static double s_dist_m;

bool trip_active(void) { return s_active; }

static void harsh(int16_t mg, int thr_ms, uint32_t dt, uint32_t *acc, uint8_t *count, bool cond)
{
    if (cond) { *acc += dt; if (*acc >= (uint32_t)thr_ms && *acc - dt < (uint32_t)thr_ms) (*count)++; }
    else *acc = 0;
    (void)mg;
}

void trip_tick(uint32_t dt, bool running)
{
    vstate_t v; vstate_snapshot(&v);
    if (!s_active && running) {
        memset(&s_t, 0, sizeof s_t); s_dist_m = 0; s_active = true; s_off_since = 0;
        s_t.start_utc = v.utc;
        fuel_trip_reset();
        logger_write(REC_TRIP_START, &v.utc, 4);
        ESP_LOGI(TAG, "trip start");
    }
    if (!s_active) return;

    if (!running) {
        if (!s_off_since) s_off_since = now_ms();
        if (now_ms() - s_off_since > 480000) {
            s_t.distance_m = (uint32_t)s_dist_m;
            s_t.fuel_ml = (uint16_t)(fuel_trip_litres() * 1000);
            logger_write(REC_TRIP_END, &s_t, sizeof s_t);
            int32_t odo = config_get(CFG_ODOMETER_KM);
            config_set(CFG_ODOMETER_KM, odo + (int32_t)(s_dist_m / 1000 + 0.5));
            s_active = false;
            ESP_LOGI(TAG, "trip end %lu m", (unsigned long)s_t.distance_m);
            return;
        }
    } else s_off_since = 0;

    s_t.duration_s = (v.utc && s_t.start_utc) ? v.utc - s_t.start_utc : s_t.duration_s;
    if (v.speed_x10 != 0xFFFF) {
        s_dist_m += v.speed_x10 / 36.0 * dt / 1000.0;
        if (v.speed_x10 > s_t.max_speed_x10) s_t.max_speed_x10 = v.speed_x10;
        static uint32_t idle_acc;
        if (v.speed_x10 < 20 && running) { idle_acc += dt; if (idle_acc >= 1000) { s_t.idle_s++; idle_acc -= 1000; } }
    }
    static uint32_t brk, acc, cor, rev;
    if (v.ax_mg != 0x7FFF) {
        harsh(v.ax_mg, 300, dt, &brk, &s_t.harsh_brake, v.ax_mg < -config_get(CFG_HARSH_BRAKE_MG));
        harsh(v.ax_mg, 300, dt, &acc, &s_t.harsh_accel, v.ax_mg > config_get(CFG_HARSH_ACCEL_MG));
        harsh(v.ay_mg, 500, dt, &cor, &s_t.harsh_corner, v.ay_mg > 350 || v.ay_mg < -350);
    }
    if (v.rpm != 0xFFFF && v.rpm > 4000) { rev += dt; if (rev >= 1000) { s_t.overrev_s++; rev -= 1000; } }
    VS_SET(trip_m, (uint32_t)s_dist_m);

    if (now_ms() - s_last_sample >= 1000) {
        s_last_sample = now_ms();
        uint8_t pl[LIVE_V1_LEN]; proto_pack_live(&v, pl);
        logger_write(REC_SAMPLE, pl, sizeof pl);
        uint16_t lvl = (uint16_t)(fuel_level_pct() * 100);
        VS_SET(fuel_level_x100, lvl);
    }
}
