#pragma once
#include <stdint.h>
#include <stddef.h>
#include "esp_err.h"

#define LOG_REC_SIZE     128
#define LOG_REC_DATA_MAX 108

enum {
    REC_SAMPLE = 1,
    REC_EVENT  = 2,
    REC_TRIP_START = 3,
    REC_TRIP_END   = 4,
    REC_CRANK  = 5,
    REC_DTC    = 6,
    REC_FILL   = 7,
    REC_LID_DUMP = 8,
    REC_BOOT   = 9,
    REC_PARK   = 10,
};

typedef struct {
    uint32_t seq, utc, uptime_ms;
    uint16_t boot_id;
    uint8_t  type, len;
    uint8_t  data[LOG_REC_DATA_MAX];
    uint32_t crc32;
} __attribute__((packed)) log_rec_t;

_Static_assert(sizeof(log_rec_t) == LOG_REC_SIZE, "log record must be 128 bytes");

esp_err_t logger_init(void);
esp_err_t logger_write(uint8_t type, const void *data, size_t len);
esp_err_t logger_read(uint32_t seq, log_rec_t *out);
uint32_t  logger_head_seq(void);
uint32_t  logger_oldest_seq(void);
