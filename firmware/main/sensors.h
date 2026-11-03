#pragma once
#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

esp_err_t sensors_init_aon(void);
esp_err_t sensors_power(bool on);
void      sensors_start(void);
void      sensors_stop(void);
esp_err_t wake_accel_arm(uint16_t threshold_mg);
esp_err_t rtc_get_utc(uint32_t *utc);
esp_err_t rtc_set_utc(uint32_t utc);
