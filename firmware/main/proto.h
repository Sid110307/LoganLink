#pragma once
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include "vstate.h"

#define PROTO_MAGIC        0xA5
#define PROTO_VERSION      0x01
#define PROTO_HDR_LEN      8
#define PROTO_CRC_LEN      2
#define PROTO_MAX_PAYLOAD  233
#define PROTO_MAX_FRAME    (PROTO_HDR_LEN + PROTO_MAX_PAYLOAD + PROTO_CRC_LEN)
#define PROTO_MAC_LEN      8

enum {
    FT_LIVE = 0x01, FT_EVENT = 0x02, FT_DTC_LIST = 0x03,
    FT_CMD = 0x10, FT_RSP = 0x11, FT_LOG_CHUNK = 0x20,
    FT_AUTH_CHALLENGE = 0x30, FT_AUTH_RESPONSE = 0x31, FT_AUTH_RESULT = 0x32,
    FT_OTA_DATA = 0x40,
};
enum { FF_ACK_REQ = 1, FF_MORE = 2, FF_AUTH = 4 };

enum {
    CMD_GET_INFO = 0x01, CMD_SET_TIME = 0x02, CMD_SET_LIVE_RATE = 0x03,
    CMD_READ_DTC = 0x10, CMD_CLEAR_DTC = 0x11, CMD_RUN_PROBE = 0x12,
    CMD_SYNC_LOG = 0x20, CMD_GET_CONFIG = 0x21, CMD_SET_CONFIG = 0x22,
    CMD_SET_ODOMETER = 0x23, CMD_CAL_VBAT = 0x24, CMD_CAL_FUEL = 0x25, CMD_FILL_UP = 0x26,
    CMD_AUX_OUT = 0x30,
    CMD_OTA_BEGIN = 0x40, CMD_OTA_END = 0x41, CMD_WIFI_OTA = 0x42,
    CMD_USER_ADD = 0x50, CMD_USER_REVOKE = 0x51, CMD_REBOOT = 0x60,
};
enum {
    RS_OK = 0, RS_BAD_ARGS = 1, RS_NOT_AUTH = 2, RS_FORBIDDEN = 3,
    RS_VEHICLE_STATE = 4, RS_BUSY = 5, RS_IO_ERROR = 6, RS_UNSUPPORTED = 7,
};

#define LIVE_V1_LEN 43

typedef struct {
    uint8_t  type, flags;
    uint16_t seq, len;
    const uint8_t *payload;
} proto_frame_t;

uint16_t proto_crc16(const uint8_t *d, size_t n);
size_t   proto_build(uint8_t *out, uint8_t type, uint8_t flags, uint16_t seq,
                     const uint8_t *payload, uint16_t len);
bool     proto_parse(const uint8_t *buf, size_t n, proto_frame_t *f);
void     proto_pack_live(const vstate_t *v, uint8_t out[LIVE_V1_LEN]);

static inline void put_u16(uint8_t *p, uint16_t v) { p[0] = v; p[1] = v >> 8; }
static inline void put_u32(uint8_t *p, uint32_t v) { p[0] = v; p[1] = v >> 8; p[2] = v >> 16; p[3] = v >> 24; }
static inline uint16_t get_u16(const uint8_t *p) { return p[0] | (p[1] << 8); }
static inline uint32_t get_u32(const uint8_t *p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24); }
