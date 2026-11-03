#pragma once
#include <stdint.h>
#include "esp_sleep.h"

void    power_start(esp_sleep_wakeup_cause_t cause);
uint8_t power_state(void);
