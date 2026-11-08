#include <string.h>
#include "proto.h"

uint16_t proto_crc16(const uint8_t *d, size_t n)
{
    uint16_t crc = 0xFFFF;
    while (n--) {
        crc ^= (uint16_t)(*d++) << 8;
        for (int i = 0; i < 8; i++) crc = (crc & 0x8000) ? (crc << 1) ^ 0x1021 : crc << 1;
    }
    return crc;
}

size_t proto_build(uint8_t *out, uint8_t type, uint8_t flags, uint16_t seq,
                   const uint8_t *payload, uint16_t len)
{
    if (len > PROTO_MAX_PAYLOAD) return 0;
    out[0] = PROTO_MAGIC; out[1] = PROTO_VERSION; out[2] = type; out[3] = flags;
    put_u16(out + 4, seq);
    put_u16(out + 6, len);
    if (len) memcpy(out + PROTO_HDR_LEN, payload, len);
    put_u16(out + PROTO_HDR_LEN + len, proto_crc16(out, PROTO_HDR_LEN + len));
    return PROTO_HDR_LEN + len + PROTO_CRC_LEN;
}

bool proto_parse(const uint8_t *buf, size_t n, proto_frame_t *f)
{
    if (n < PROTO_HDR_LEN + PROTO_CRC_LEN) return false;
    if (buf[0] != PROTO_MAGIC || buf[1] != PROTO_VERSION) return false;
    uint16_t len = get_u16(buf + 6);
    if (len > PROTO_MAX_PAYLOAD || n != (size_t)(PROTO_HDR_LEN + len + PROTO_CRC_LEN)) return false;
    if (get_u16(buf + PROTO_HDR_LEN + len) != proto_crc16(buf, PROTO_HDR_LEN + len)) return false;
    f->type = buf[2]; f->flags = buf[3]; f->seq = get_u16(buf + 4); f->len = len;
    f->payload = buf + PROTO_HDR_LEN;
    return true;
}

void proto_pack_live(const vstate_t *v, uint8_t o[LIVE_V1_LEN])
{
    put_u32(o + 0,  v->uptime_ms);
    put_u32(o + 4,  v->utc);
    put_u16(o + 8,  v->rpm);
    put_u16(o + 10, v->speed_x10);
    put_u16(o + 12, (uint16_t)v->ect_x10);
    put_u16(o + 14, (uint16_t)v->iat_x10);
    put_u16(o + 16, v->map_x10);
    put_u16(o + 18, v->tps_x10);
    o[20] = (uint8_t)v->stft; o[21] = (uint8_t)v->ltft; o[22] = (uint8_t)v->adv; o[23] = v->load;
    put_u16(o + 24, v->vbat_mv);
    put_u16(o + 26, v->fuel_lph_x100);
    put_u16(o + 28, (uint16_t)v->ax_mg);
    put_u16(o + 30, (uint16_t)v->ay_mg);
    o[32] = v->state;
    put_u16(o + 33, v->src);
    put_u32(o + 35, v->trip_m);
    put_u16(o + 39, v->dtc);
    put_u16(o + 41, v->fuel_level_x100);
}
