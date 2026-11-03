#pragma once
#include <stdint.h>
#include <stdbool.h>

typedef struct {
    uint32_t start_utc, duration_s, distance_m, idle_s;
    uint16_t fuel_ml, max_speed_x10;
    uint8_t  harsh_brake, harsh_accel, harsh_corner, overrev_s;
} trip_summary_t;

void trip_tick(uint32_t dt_ms, bool engine_running);
bool trip_active(void);
