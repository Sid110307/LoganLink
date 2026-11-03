#pragma once
#include "driver/gpio.h"
#include "hal/adc_types.h"

#define FW_VERSION_MAJOR 1
#define FW_VERSION_MINOR 1
#define FW_VERSION_PATCH 0
#define HW_REVISION      'B'

#define PIN_BTN_PAIR       GPIO_NUM_0
#define PIN_LED_R          GPIO_NUM_38
#define PIN_LED_G          GPIO_NUM_39

#define ADC_CH_VBAT_DC     ADC_CHANNEL_0
#define ADC_CH_VBAT_AC     ADC_CHANNEL_1

#define PIN_CAN_TX         GPIO_NUM_4
#define PIN_CAN_RX         GPIO_NUM_5
#define PIN_IGN_SENSE_N    GPIO_NUM_6
#define PIN_K_TX           GPIO_NUM_17
#define PIN_K_RX           GPIO_NUM_18
#define K_UART             UART_NUM_1

#define PIN_AUX_PWR        GPIO_NUM_21

#define PIN_ACC_WAKE_INT   GPIO_NUM_7
#define I2C_PORT           I2C_NUM_0
#define PIN_I2C_SDA        GPIO_NUM_8
#define PIN_I2C_SCL        GPIO_NUM_9
#define LIS2DW12_ADDR      0x19

#define VBAT_DIV_NUM       1150
#define VBAT_DIV_DEN       150

#define ENGINE_DISPLACEMENT_L   1.598f
#define FUEL_DENSITY_KG_L       0.745f
#define AFR_STOICH              14.7f
