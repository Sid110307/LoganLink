#pragma once
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

typedef struct {
    bool     authed;
    uint8_t  user_id, role;
    uint8_t  nonce_d[16];
    uint8_t  k_s[32];
    uint32_t last_ctr;
} auth_session_t;

void auth_new_challenge(auth_session_t *s, uint8_t out_payload[24]);
bool auth_check_response(auth_session_t *s, const uint8_t *p, size_t n);
bool auth_check_cmd(auth_session_t *s, const uint8_t *frame, size_t frame_len_without_crc);
void auth_derive_user_key(uint8_t user_id, uint8_t out[32]);
