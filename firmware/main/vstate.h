#pragma once
#include <stdint.h>
#include <stdbool.h>

enum { ST_BOOT = 0, ST_STANDBY = 1, ST_DRIVE = 2, ST_SLEEP = 3, ST_SAFE = 4 };

#define SRC_RPM_ECU      (1u << 0)
#define SRC_RPM_RIPPLE   (1u << 1)
#define SRC_SPEED_ECU    (1u << 2)
#define SRC_SPEED_GNSS   (1u << 3)
#define SRC_ECU_ONLINE   (1u << 4)
#define SRC_FUEL_MODEL   (1u << 5)
#define SRC_FUEL_SENDER  (1u << 6)
#define SRC_GNSS_VALID   (1u << 7)

typedef struct {
    uint32_t uptime_ms, utc;
    uint16_t rpm, speed_x10;
    int16_t  ect_x10, iat_x10;
    uint16_t map_x10, tps_x10;
    int8_t   stft, ltft, adv;
    uint8_t  load;
    uint16_t vbat_mv, fuel_lph_x100;
    int32_t  lat_e7, lon_e7;
    uint16_t gspeed_x10, heading_x10;
    uint8_t  sats, fix;
    int16_t  ax_mg, ay_mg;
    uint16_t co2;
    int16_t  cabin_t_x10;
    uint8_t  cabin_rh, state;
    uint16_t src;
    uint32_t trip_m;
    uint16_t dtc;
    uint16_t fuel_level_x100;
    uint32_t ecu_rpm_ms;
    uint32_t ecu_speed_ms;
    float    hdop;
} vstate_t;

void     vstate_init(void);
void     vstate_snapshot(vstate_t *out);
void     vstate_lock(void);
void     vstate_unlock(void);
vstate_t *vstate_raw(void);
uint32_t now_ms(void);

#define VS_SET(field, value) do { vstate_lock(); vstate_raw()->field = (value); vstate_unlock(); } while (0)
