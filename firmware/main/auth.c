#include <string.h>
#include "mbedtls/md.h"
#include "esp_random.h"
#include "esp_mac.h"
#include "board.h"
#include "auth.h"
#include "config.h"
#include "proto.h"

static void hmac(const uint8_t *key, size_t kl, const uint8_t *msg, size_t ml, uint8_t out[32])
{
    mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), key, kl, msg, ml, out);
}

static bool ct_equal(const uint8_t *a, const uint8_t *b, size_t n)
{
    uint8_t d = 0;
    for (size_t i = 0; i < n; i++) d |= a[i] ^ b[i];
    return d == 0;
}

void auth_derive_user_key(uint8_t user_id, uint8_t out[32])
{
    uint8_t kdev[32], m[8] = { 'L', 'L', '-', 'U', 'S', 'E', 'R', user_id };
    config_get_kdev(kdev);
    hmac(kdev, 32, m, sizeof m, out);
    memset(kdev, 0, sizeof kdev);
}

void auth_new_challenge(auth_session_t *s, uint8_t out[24])
{
    memset(s, 0, sizeof *s);
    esp_fill_random(s->nonce_d, 16);
    esp_read_mac(out, ESP_MAC_BT);
    memcpy(out + 6, s->nonce_d, 16);
    out[22] = FW_VERSION_MAJOR; out[23] = FW_VERSION_MINOR;
}

bool auth_check_response(auth_session_t *s, const uint8_t *p, size_t n)
{
    if (n != 33) return false;
    uint8_t uid = p[0];
    if (uid > 7 || config_user_revoked(uid)) return false;
    uint8_t ku[32], tag[32], msg[7 + 6 + 16 + 16];
    auth_derive_user_key(uid, ku);
    memcpy(msg, "LL-AUTH", 7);
    esp_read_mac(msg + 7, ESP_MAC_BT);
    memcpy(msg + 13, s->nonce_d, 16);
    memcpy(msg + 29, p + 1, 16);
    hmac(ku, 32, msg, sizeof msg, tag);
    if (!ct_equal(tag, p + 17, 16)) { memset(ku, 0, sizeof ku); return false; }

    uint8_t sm[7 + 32];
    memcpy(sm, "LL-SESS", 7); memcpy(sm + 7, s->nonce_d, 16); memcpy(sm + 23, p + 1, 16);
    hmac(ku, 32, sm, sizeof sm, s->k_s);
    memset(ku, 0, sizeof ku);
    s->authed = true; s->user_id = uid; s->role = uid == 0 ? 0 : 1; s->last_ctr = 0;
    return true;
}

bool auth_check_cmd(auth_session_t *s, const uint8_t *f, size_t n)
{
    if (!s->authed || n < PROTO_HDR_LEN + 4 + 1 + PROTO_MAC_LEN) return false;
    uint32_t ctr = get_u32(f + PROTO_HDR_LEN);
    if (ctr <= s->last_ctr) return false;
    uint8_t mac[32];
    hmac(s->k_s, 32, f, n - PROTO_MAC_LEN, mac);
    if (!ct_equal(mac, f + n - PROTO_MAC_LEN, PROTO_MAC_LEN)) return false;
    s->last_ctr = ctr;
    return true;
}
