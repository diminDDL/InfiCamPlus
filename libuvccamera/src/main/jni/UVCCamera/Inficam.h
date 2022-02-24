#ifndef __INFICAM_H__
#define __INFICAM_H__

#include <stdint.h>
#include <string>

class Inficam {
    /* Set by init(). */
    int width, height, lens, range, tbl1_offset, tbl2_offset, fpa_off;
    float fpa_div;

public:
    /* User parameters, set manually or read from camera with readparams(). */
    float correction = 0.0; // TODO do not forget to perhaps bake this into the LUT
    float temp_reflected = 20.0;
    float temp_air = 20.0;
    float humidity = 0.45;
    float emissivity = 0.98;
    float distance = 1.0;

    /* Optional offsets to compensenate for errors. */
    float offset_temp_fpa = 0, offset_temp_shutter = 0;

    /* Lookup table for temperature in celcius from pixel values, written by update_table(). */
    static const int table_len = 0x4000;
    static const int table_mask = 0x4000 - 1;
    float table[table_len]; /* Do not forget to always check bounds before access! */

    /* Values read by update(). */
    float temp_fpa;
    float temp_shutter;
    float temp_core;
    uint16_t cal_00; /* Calibration parameters read from device. */
    float cal_01, cal_02, cal_03, cal_04, cal_05;
    uint16_t temp_max_x, temp_max_y, temp_max; /* To get Celcius values use temp(). */
    uint16_t temp_min_x, temp_min_y, temp_min;
    uint16_t temp_avg, temp_center;
    uint16_t temp_user[3];

    /* Before any of the other functions, call this one.
     *   width, height: As reported by the UVC driver.
     *   lens: 68 for 6.8mm, 130 for 13mm lens.
     *   range: 120 or 400 (-20-... Celcius).
     */
    void init(int width, int height, int lens, int range);

    /* Always check that frame is large enough first. */
    void update(uint16_t *frame);
    void update_table(uint16_t *frame); /* Call update() first. */
    void temp(uint16_t *frame, float *output);
    float temp_single(uint16_t val); /* Does not need table, does use values from update(). */
    void readparams(uint16_t *frame);

    inline float temp(uint16_t val) {
        return table[val & table_mask];
    }

    /* Get info about camera.*/
    std::string version_fw(uint16_t *frame);
    std::string serial(uint16_t *frame);
};

#endif /* __INFICAM_H__ */
