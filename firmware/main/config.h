#pragma once
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include "esp_err.h"

enum { LF_RPM = 1, LF_SPEED, LF_ECT, LF_IAT, LF_MAP, LF_TPS, LF_ADV, LF_STFT, LF_LTFT };
typedef struct {
    uint8_t lid, off, size, is_signed, field;
    float   scale, add;
} lidmap_t;
#define LIDMAP_MAX 24

enum {
    CFG_LIVE_HZ = 1,
    CFG_FUEL_PRICE_PAISE,
    CFG_ODOMETER_KM,
    CFG_VBAT_GAIN_PPM,
    CFG_VBAT_OFFSET_MV,
    CFG_KRIP_MILLI,
    CFG_VE_PERMILLE,
    CFG_IGN_TAP_FITTED,
    CFG_FUEL_TAP_FITTED,
    CFG_TANK_L_X10,
    CFG_HARSH_BRAKE_MG,
    CFG_HARSH_ACCEL_MG,
    CFG_LOWBAT_HIBERNATE_MV,
    CFG_AUX_POLICY,
    CFG_KEY_MAX
};

esp_err_t config_init(void);
int32_t   config_get(int key);
esp_err_t config_set(int key, int32_t val);

int       config_lidmap(const lidmap_t **out);
esp_err_t config_set_lidmap(const lidmap_t *m, int n);
int       config_ecu_candidates(const uint8_t **out);
esp_err_t config_save_profile(const void *p, size_t n);
esp_err_t config_load_profile(void *p, size_t n);
bool      config_ign_tap_active(void);

esp_err_t config_get_kdev(uint8_t out[32]);
bool      config_user_revoked(uint8_t user_id);
esp_err_t config_revoke_user(uint8_t user_id);
esp_err_t config_unrevoke_user(uint8_t user_id);
uint16_t  config_boot_id(void);
