#pragma once
#include <stdbool.h>
#include <stdint.h>
void ble_link_start(void);
void ble_link_open_pairing_window(uint32_t seconds);
bool ble_link_any_connected(void);
void ble_link_set_advertising(bool on);
