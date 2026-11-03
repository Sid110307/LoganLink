#include <string.h>
#include "freertos/FreeRTOS.h"
#include "esp_timer.h"
#include "vstate.h"

static vstate_t s_vs;
static portMUX_TYPE s_mux = portMUX_INITIALIZER_UNLOCKED;

uint32_t now_ms(void) { return (uint32_t)(esp_timer_get_time() / 1000); }

void vstate_init(void)
{
    memset(&s_vs, 0, sizeof s_vs);
    s_vs.rpm = s_vs.speed_x10 = s_vs.map_x10 = s_vs.tps_x10 = 0xFFFF;
    s_vs.ect_x10 = s_vs.iat_x10 = s_vs.cabin_t_x10 = 0x7FFF;
    s_vs.stft = s_vs.ltft = s_vs.adv = INT8_MIN;
    s_vs.load = s_vs.cabin_rh = 0xFF;
    s_vs.fuel_lph_x100 = s_vs.gspeed_x10 = s_vs.heading_x10 = s_vs.fuel_level_x100 = 0xFFFF;
    s_vs.lat_e7 = s_vs.lon_e7 = INT32_MAX;
    s_vs.ax_mg = s_vs.ay_mg = 0x7FFF;
    s_vs.hdop = 99.f;
}

void vstate_lock(void)   { portENTER_CRITICAL(&s_mux); }
void vstate_unlock(void) { portEXIT_CRITICAL(&s_mux); }
vstate_t *vstate_raw(void) { return &s_vs; }

void vstate_snapshot(vstate_t *out)
{
    portENTER_CRITICAL(&s_mux);
    *out = s_vs;
    portEXIT_CRITICAL(&s_mux);
    out->uptime_ms = now_ms();
}
