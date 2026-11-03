#include <stdint.h>
#include "ulp_riscv.h"
#include "ulp_riscv_utils.h"
#include "ulp_riscv_adc_ulp_core.h"
#include "hal/adc_types.h"

volatile uint32_t thr_charge;
volatile uint32_t thr_step;
volatile uint32_t step_enable;
volatile uint32_t baseline_q8;
volatile uint32_t last_raw;
volatile uint32_t wake_reason;

int main(void)
{
    int32_t raw = 0;
    for (int i = 0; i < 4; i++) raw += ulp_riscv_adc_read_channel(ADC_UNIT_1, ADC_CHANNEL_0);
    raw /= 4;
    last_raw = raw;

    if (baseline_q8 == 0) baseline_q8 = (uint32_t)raw << 8;
    int32_t base = baseline_q8 >> 8;

    if ((uint32_t)raw > thr_charge) { wake_reason = 1; ulp_riscv_wakeup_main_processor(); return 0; }
    int32_t d = raw - base;
    if (step_enable && (d > (int32_t)thr_step || -d > (int32_t)thr_step)) {
        wake_reason = 2; baseline_q8 = (uint32_t)raw << 8;
        ulp_riscv_wakeup_main_processor();
        return 0;
    }
    baseline_q8 = baseline_q8 - (baseline_q8 >> 6) + ((uint32_t)raw << 2);
    return 0;
}
