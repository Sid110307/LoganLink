#pragma once
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

bool     vguard_allow(const uint8_t *req, size_t len);
void     vguard_arm_clear_dtc(void);
uint32_t vguard_denied_count(void);
