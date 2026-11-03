#pragma once
#include <stdint.h>
#include <stdbool.h>

typedef struct {
    uint16_t rest_mv;
    uint16_t min_mv;
    uint16_t crank_ms;
    uint16_t recover_ms;
    int8_t   temp_c;
} crank_rec_t;

typedef enum { CHG_UNKNOWN, CHG_NOT_CHARGING, CHG_NORMAL, CHG_LOW, CHG_OVER } charge_state_t;

void           vbat_start(void);
void           vbat_stop(void);
charge_state_t vbat_charge_state(void);
uint16_t       vbat_ripple_mvpp(void);
float          vbat_ripple_freq_hz(void);
uint16_t       vbat_mv_fast(void);
