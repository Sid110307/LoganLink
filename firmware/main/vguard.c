#include "esp_log.h"
#include "vguard.h"
#include "vstate.h"

static const char *TAG = "vguard";
static uint32_t s_denied, s_clear_armed_until;

void vguard_arm_clear_dtc(void) { s_clear_armed_until = now_ms() + 5000; }
uint32_t vguard_denied_count(void) { return s_denied; }

static bool deny(uint8_t sid, const char *why)
{
    s_denied++;
    ESP_LOGW(TAG, "DENIED SID 0x%02X (%s)", sid, why);
    return false;
}

bool vguard_allow(const uint8_t *req, size_t len)
{
    if (!req || !len) return false;
    uint8_t sid = req[0];
    switch (sid) {
    case 0x01: case 0x02: case 0x03: case 0x07: case 0x09:
    case 0x1A:
    case 0x21:
    case 0x17:
    case 0x18:
    case 0x3E:
    case 0x81:
    case 0x82:
        return true;
    case 0x10:
        if (len >= 2 && req[1] == 0x81) return true;
        return deny(sid, "non-default session");
    case 0x04:
    case 0x14:
    {
        vstate_t v; vstate_snapshot(&v);
        bool engine_off = (v.rpm == 0 || v.rpm == 0xFFFF) && v.vbat_mv < 13000;
        if (now_ms() < s_clear_armed_until && engine_off) {
            s_clear_armed_until = 0;
            return true;
        }
        return deny(sid, "clear not armed or engine running");
    }
    default:
        return deny(sid, "not in allow-list");
    }
}
