#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "esp_partition.h"
#include "esp_rom_crc.h"
#include "esp_log.h"
#include "logger.h"
#include "vstate.h"
#include "config.h"

static const char *TAG = "logger";
#define SECTOR      4096u
#define PER_SECTOR  (SECTOR / LOG_REC_SIZE)
#define LOG_SUBTYPE 0x40
static const esp_partition_t *s_part;
static uint32_t s_nsect, s_head, s_oldest;
static SemaphoreHandle_t s_mtx;

static inline uint32_t addr_of(uint32_t seq) { return ((seq / PER_SECTOR) % s_nsect) * SECTOR + (seq % PER_SECTOR) * LOG_REC_SIZE; }
static inline uint32_t rec_crc(const log_rec_t *r) { return esp_rom_crc32_le(0, (const uint8_t *)r, LOG_REC_SIZE - 4); }

static bool read_valid(uint32_t off, log_rec_t *r)
{
    if (esp_partition_read(s_part, off, r, sizeof *r) != ESP_OK) return false;
    return r->seq != 0xFFFFFFFF && r->crc32 == rec_crc(r);
}

esp_err_t logger_init(void)
{
    s_mtx = xSemaphoreCreateMutex();
    s_part = esp_partition_find_first(ESP_PARTITION_TYPE_DATA, LOG_SUBTYPE, "log");
    if (!s_part) { ESP_LOGE(TAG, "no 'log' partition"); return ESP_ERR_NOT_FOUND; }
    s_nsect = s_part->size / SECTOR;

    log_rec_t r; uint32_t best = 0, lowest = UINT32_MAX; bool any = false;
    for (uint32_t s = 0; s < s_nsect; s++) {
        if (read_valid(s * SECTOR, &r)) {
            if (!any || r.seq > best) best = r.seq;
            if (r.seq < lowest) lowest = r.seq;
            any = true;
        }
    }
    if (!any) { s_head = 0; s_oldest = 0; }
    else {
        uint32_t seq = best;
        while ((seq % PER_SECTOR) != PER_SECTOR - 1 && read_valid(addr_of(seq + 1), &r) && r.seq == seq + 1) seq++;
        s_head = seq + 1;
        s_oldest = lowest;
    }
    ESP_LOGI(TAG, "%lu sectors, head=%lu oldest=%lu", (unsigned long)s_nsect, (unsigned long)s_head, (unsigned long)s_oldest);
    return ESP_OK;
}

esp_err_t logger_write(uint8_t type, const void *data, size_t len)
{
    if (!s_part) return ESP_ERR_INVALID_STATE;
    if (len > LOG_REC_DATA_MAX) len = LOG_REC_DATA_MAX;
    vstate_t v; vstate_snapshot(&v);
    log_rec_t r;
    memset(&r, 0, sizeof r);
    r.utc = v.utc; r.uptime_ms = v.uptime_ms; r.boot_id = config_boot_id();
    r.type = type; r.len = (uint8_t)len;
    memcpy(r.data, data, len);

    xSemaphoreTake(s_mtx, portMAX_DELAY);
    r.seq = s_head;
    r.crc32 = rec_crc(&r);
    uint32_t a = addr_of(s_head);
    esp_err_t e = ESP_OK;
    if ((s_head % PER_SECTOR) == 0) {
        e = esp_partition_erase_range(s_part, a, SECTOR);
        uint32_t lost_first = s_head >= s_nsect * PER_SECTOR ? s_head - s_nsect * PER_SECTOR + PER_SECTOR : 0;
        if (lost_first > s_oldest) s_oldest = lost_first;
    }
    if (e == ESP_OK) e = esp_partition_write(s_part, a, &r, sizeof r);
    if (e == ESP_OK) s_head++;
    xSemaphoreGive(s_mtx);
    return e;
}

esp_err_t logger_read(uint32_t seq, log_rec_t *out)
{
    if (!s_part || seq >= s_head || seq < s_oldest) return ESP_ERR_NOT_FOUND;
    xSemaphoreTake(s_mtx, portMAX_DELAY);
    bool ok = read_valid(addr_of(seq), out) && out->seq == seq;
    xSemaphoreGive(s_mtx);
    return ok ? ESP_OK : ESP_ERR_INVALID_CRC;
}

uint32_t logger_head_seq(void)   { return s_head; }
uint32_t logger_oldest_seq(void) { return s_oldest; }
