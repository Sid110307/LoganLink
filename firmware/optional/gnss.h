#pragma once
#include <stdint.h>
typedef struct { int32_t lat_e7, lon_e7; uint32_t utc; uint16_t pdop_x100; } gnss_fix_t;
void gnss_start(uint16_t rate_ms);
void gnss_set_rate(uint16_t rate_ms);
void gnss_stop(void);
const gnss_fix_t *gnss_last_good_fix(void);
