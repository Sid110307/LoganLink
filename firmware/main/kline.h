#pragma once
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include "esp_err.h"

typedef enum {
    KL_PROTO_NONE = 0,
    KL_PROTO_KWP_FAST,
    KL_PROTO_KWP_5BAUD,
    KL_PROTO_ISO9141,
} kl_proto_t;

typedef struct {
    kl_proto_t proto;
    uint8_t    target;
    uint8_t    functional;
    uint8_t    kb1, kb2;
    uint8_t    p4_ms;
} kl_session_t;

#define KL_TESTER_ADDR 0xF1
#define KL_MAX_MSG     260

esp_err_t kl_init(void);
esp_err_t kl_connect(kl_session_t *s);
esp_err_t kl_request(kl_session_t *s, const uint8_t *req, size_t req_len,
                     uint8_t *resp, size_t resp_max, size_t *resp_len);
void      kl_disconnect(kl_session_t *s);
esp_err_t kl_read_next(kl_session_t *s, uint8_t *resp, size_t resp_max, size_t *resp_len, uint32_t timeout_ms);
bool      kl_foreign_activity(void);
uint32_t  kl_last_tx_ms(void);
