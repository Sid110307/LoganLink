#pragma once
#include <stdint.h>
#include <stdbool.h>
#include "esp_err.h"

#define OBD_MAX_DTC 32

typedef struct {
    uint8_t  proto;
    uint8_t  target;
    uint8_t  functional;
    uint8_t  kb1, kb2;
    uint32_t pid_support[4];
    char     ecu_id[32];
    uint8_t  lid_present[16];
} obd_profile_t;

typedef struct { uint16_t code; uint8_t status; } obd_dtc_t;

void      obd_start(void);
esp_err_t obd_run_probe(void);
const obd_profile_t *obd_profile(void);
int       obd_read_dtcs(bool pending, obd_dtc_t *out, int max);
esp_err_t obd_clear_dtcs(void);
void      obd_dtc_to_str(uint16_t code, char out[6]);
bool      obd_online(void);
