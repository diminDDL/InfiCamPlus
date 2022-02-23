#ifndef __INFICAM_H__
#define __INFICAM_H__

#include <stdint.h>

class Infiray {
    /* Set by init(). */
    int width, height, lens, range, tbl2_offset;

    /* User parameters, set manually or read from camera with readparams(). */
    float correction; // TODO do not forget to perhaps bake this into the LUT
    float temp_reflected;
    float temp_air;
    float humidity;
    float emissivity;
    float distance;

    /* Lookup table for temperature in celcius from pixel values, written by update(). */
    float lut[0x4000];

    /* Miscellaneous values read by update(). */
    float temp_fpa_average;
    float temp_fpa;
    int temp_max_x, temp_max_y;
    float temp_max;
    int temp_min_x, temp_min_y;
    float temp_min;
    float temp_avg;
    float temp_center;
    float temp_user[3];
    float temp_shutter;
    float temp_core;
    uint16_t cal_00; /* Calibration parameters read from device. */
    float cal_01, cal_02, cal_03, cal_04, cal_05;
    char version_fw[16], serial[32];

    /* Optional offsets to compensenate for errors. */
    float offset_temp_fpa, offset_temp_shutter;

    void init(int width, int height, int lens, int range);
    void update(uint16_t *frame);
    void readparams(uint16_t *frame);
};

#endif /* __INFICAM_H__ */
