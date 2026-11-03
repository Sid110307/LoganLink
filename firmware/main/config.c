#include <stdio.h>
#include <string.h>
#include "nvs_flash.h"
#include "nvs.h"
#include "esp_random.h"
#include "esp_log.h"
#include "driver/gpio.h"
#include "board.h"
#include "config.h"

static const char *TAG = "config";
static nvs_handle_t s_nvs;
static int32_t  s_cache[CFG_KEY_MAX];
static lidmap_t s_lidmap[LIDMAP_MAX];
static int      s_lidmap_n;
static uint8_t  s_revoked;
static uint16_t s_boot_id;

static const int32_t s_defaults[CFG_KEY_MAX] = {
    [CFG_LIVE_HZ] = 10, [CFG_FUEL_PRICE_PAISE] = 10500, [CFG_VBAT_GAIN_PPM] = 1000000,
    [CFG_VE_PERMILLE] = 1000, [CFG_TANK_L_X10] = 500, [CFG_HARSH_BRAKE_MG] = 400,
    [CFG_HARSH_ACCEL_MG] = 350, [CFG_LOWBAT_HIBERNATE_MV] = 12100,
};

static const uint8_t s_ecu_cand[] = { 0x10, 0x11 };

esp_err_t config_init(void)
{
    esp_err_t e = nvs_flash_init();
    if (e == ESP_ERR_NVS_NO_FREE_PAGES || e == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_LOGW(TAG, "NVS reformat");
        nvs_flash_erase();
        e = nvs_flash_init();
    }
    ESP_ERROR_CHECK(e);
    ESP_ERROR_CHECK(nvs_open("ll", NVS_READWRITE, &s_nvs));

    for (int k = 1; k < CFG_KEY_MAX; k++) {
        char key[8]; snprintf(key, sizeof key, "k%d", k);
        if (nvs_get_i32(s_nvs, key, &s_cache[k]) != ESP_OK) s_cache[k] = s_defaults[k];
    }
    size_t sz = sizeof s_lidmap;
    if (nvs_get_blob(s_nvs, "lidmap", s_lidmap, &sz) == ESP_OK) s_lidmap_n = sz / sizeof(lidmap_t);
    nvs_get_u8(s_nvs, "revoked", &s_revoked);

    uint8_t k[32]; sz = sizeof k;
    if (nvs_get_blob(s_nvs, "kdev", k, &sz) != ESP_OK) {
        esp_fill_random(k, sizeof k);
        nvs_set_blob(s_nvs, "kdev", k, sizeof k);
        ESP_LOGW(TAG, "K_dev generated (print QR label at provisioning)");
    }
    memset(k, 0, sizeof k);

    nvs_get_u16(s_nvs, "boot_id", &s_boot_id);
    s_boot_id++;
    nvs_set_u16(s_nvs, "boot_id", s_boot_id);
    return nvs_commit(s_nvs);
}

int32_t config_get(int key) { return (key > 0 && key < CFG_KEY_MAX) ? s_cache[key] : 0; }

esp_err_t config_set(int key, int32_t val)
{
    if (key <= 0 || key >= CFG_KEY_MAX) return ESP_ERR_INVALID_ARG;
    char k[8]; snprintf(k, sizeof k, "k%d", key);
    s_cache[key] = val;
    nvs_set_i32(s_nvs, k, val);
    return nvs_commit(s_nvs);
}

int config_lidmap(const lidmap_t **out) { if (out) *out = s_lidmap; return s_lidmap_n; }

esp_err_t config_set_lidmap(const lidmap_t *m, int n)
{
    if (n < 0 || n > LIDMAP_MAX) return ESP_ERR_INVALID_ARG;
    memcpy(s_lidmap, m, n * sizeof *m); s_lidmap_n = n;
    nvs_set_blob(s_nvs, "lidmap", s_lidmap, n * sizeof *m);
    return nvs_commit(s_nvs);
}

int config_ecu_candidates(const uint8_t **out) { *out = s_ecu_cand; return sizeof s_ecu_cand; }

esp_err_t config_save_profile(const void *p, size_t n)
{
    nvs_set_blob(s_nvs, "vprof", p, n);
    return nvs_commit(s_nvs);
}
esp_err_t config_load_profile(void *p, size_t n)
{
    size_t sz = n;
    esp_err_t e = nvs_get_blob(s_nvs, "vprof", p, &sz);
    return (e == ESP_OK && sz == n) ? ESP_OK : ESP_ERR_NOT_FOUND;
}

bool config_ign_tap_active(void)
{
    return s_cache[CFG_IGN_TAP_FITTED] && gpio_get_level(PIN_IGN_SENSE_N) == 0;
}

esp_err_t config_get_kdev(uint8_t out[32])
{
    size_t sz = 32;
    return nvs_get_blob(s_nvs, "kdev", out, &sz);
}

bool config_user_revoked(uint8_t id) { return id < 8 && (s_revoked & (1u << id)); }

esp_err_t config_revoke_user(uint8_t id)
{
    if (id == 0 || id > 7) return ESP_ERR_INVALID_ARG;
    s_revoked |= 1u << id;
    nvs_set_u8(s_nvs, "revoked", s_revoked);
    return nvs_commit(s_nvs);
}
esp_err_t config_unrevoke_user(uint8_t id)
{
    if (id > 7) return ESP_ERR_INVALID_ARG;
    s_revoked &= ~(1u << id);
    nvs_set_u8(s_nvs, "revoked", s_revoked);
    return nvs_commit(s_nvs);
}

uint16_t config_boot_id(void) { return s_boot_id; }
