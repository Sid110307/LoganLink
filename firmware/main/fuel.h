#pragma once
#include <stdint.h>
void  fuel_update(uint32_t dt_ms);
float fuel_trip_litres(void);
void  fuel_trip_reset(void);
void  fuel_fill_up(float litres);
float fuel_level_pct(void);
