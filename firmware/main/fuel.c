#include <math.h>
#include "board.h"
#include "fuel.h"
#include "vstate.h"
#include "config.h"

static double s_trip_l, s_since_fill_l;

static float ve_base(uint16_t rpm, float map_kpa)
{
    float r = rpm / 1000.f;
    float ve = 0.62f + 0.09f * r - 0.012f * r * r;
    if (map_kpa < 40) ve -= 0.04f;
    return fminf(fmaxf(ve, 0.45f), 0.90f);
}

void fuel_update(uint32_t dt_ms)
{
    vstate_t v; vstate_snapshot(&v);
    if (v.rpm == 0xFFFF || v.rpm < 300 || v.map_x10 == 0xFFFF || !(v.src & SRC_ECU_ONLINE)) {
        VS_SET(fuel_lph_x100, 0xFFFF);
        return;
    }
    float map_pa = v.map_x10 * 100.f;
    float t_k = (v.iat_x10 != 0x7FFF ? v.iat_x10 / 10.f : 30.f) + 273.15f;
    float ve = ve_base(v.rpm, v.map_x10 / 10.f) * config_get(CFG_VE_PERMILLE) / 1000.f;
    float m_air = map_pa * (ENGINE_DISPLACEMENT_L / 1000.f) * (v.rpm / 120.f) * ve / (287.05f * t_k);
    float trim = 1.f;
    if (v.stft != INT8_MIN) trim += v.stft / 100.f;
    if (v.ltft != INT8_MIN) trim += v.ltft / 100.f;
    bool dfco = v.tps_x10 != 0xFFFF && v.tps_x10 < 15 && v.rpm > 1500;
    float m_fuel = dfco ? 0 : m_air / AFR_STOICH * trim;
    float lph = m_fuel * 3600.f / FUEL_DENSITY_KG_L;
    s_trip_l += lph * dt_ms / 3.6e6;
    s_since_fill_l += lph * dt_ms / 3.6e6;
    vstate_lock();
    vstate_raw()->fuel_lph_x100 = (uint16_t)fminf(lph * 100.f, 65000.f);
    vstate_raw()->src |= SRC_FUEL_MODEL;
    vstate_unlock();
}

float fuel_trip_litres(void) { return (float)s_trip_l; }
void  fuel_trip_reset(void)  { s_trip_l = 0; }

void fuel_fill_up(float litres)
{
    if (s_since_fill_l > 5.0 && litres > 5.0f) {
        float ratio = litres / (float)s_since_fill_l;
        if (ratio > 0.7f && ratio < 1.3f) {
            int32_t ve = config_get(CFG_VE_PERMILLE);
            ve = (int32_t)(ve * (0.7f + 0.3f * ratio));
            config_set(CFG_VE_PERMILLE, ve);
        }
    }
    s_since_fill_l = 0;
}

float fuel_level_pct(void)
{
    float tank = config_get(CFG_TANK_L_X10) / 10.f;
    float est = 100.f * (1.f - (float)s_since_fill_l / tank);
    return fmaxf(0.f, fminf(100.f, est));
}
