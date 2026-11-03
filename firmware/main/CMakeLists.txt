idf_component_register(
    SRCS "app_main.c" "power.c" "kline.c" "obd.c" "vguard.c" "vbat.c" "fuel.c"
         "sensors.c" "logger.c" "proto.c" "auth.c" "ble_link.c" "config.c"
         "vstate.c" "trip.c"
    INCLUDE_DIRS "."
    REQUIRES driver esp_adc esp_timer nvs_flash bt mbedtls esp_partition
             app_update ulp esp_pm
)

# ULP-RISC-V program: parked VBAT supervision (docs/07 §5)
set(ulp_app_name ulp_main)
set(ulp_riscv_sources "ulp/ulp_vbat.c")
set(ulp_exp_dep_srcs "power.c")
ulp_embed_binary(${ulp_app_name} "${ulp_riscv_sources}" "${ulp_exp_dep_srcs}")
