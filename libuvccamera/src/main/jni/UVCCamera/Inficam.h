#ifndef __INFICAM_H__
#define __INFICAM_H__

#include <cstdint>
#include <cstddef> /* size_t */
#include <string>

class Inficam {
    /* Set by init(). */
    int s1_offset, s2_offset, fpa_off;
    float fpa_div, distance_multiplier, cal_00_offset, cal_00_fpamul;

    /* Values used internally, updated by update(). */
    float distance_adjusted, numerator_sub, denominator;
    float cal_01, cal_a, cal_b, cal_c, cal_d;
    int table_offset;

public:
    /* Dimensions of actual thermographic image, set by init(). */
    int width, height;

    /* User parameters, set manually or read from camera with readParams().
     * Changes take effect for temp_single() after the next update(), and temp() after update().
     */
    float correction = 0.0;
    float temp_reflected = 0.0;
    float temp_air = 0.0;
    float humidity = 0.0;
    float emissivity = 0.0;
    float distance = 0.0;

    /* Optional offsets to compensenate for errors. */
    float offset_temp_fpa = 0.0;
    float offset_temp_shutter = 0.0;

    /* Lookup table for temperature in celcius from pixel values, written by update_table(). */
    static const int table_len = 0x4000;
    static const int table_mask = 0x4000 - 1;
    float table[table_len]; /* Do not forget to always check bounds before access! */

    /* Values read by update(). */
    float temp_fpa, temp_shutter, temp_core;
    uint16_t fpa_average; /* I'm not exactly sure what this value is. */
    uint16_t temp_max_x, temp_max_y, temp_max; /* To get Celcius values use temp(). */
    uint16_t temp_min_x, temp_min_y, temp_min;
    uint16_t temp_avg, temp_center;
    uint16_t temp_user[3];

    /* Before any of the other functions, call this one.
     *   width, height: As reported by the UVC driver.
     *   dmul: Distance multiplier, 3.0 for 6.8mm lens, 1.0 for 13mm lens.
     *   range: 120 or 400 (-20-... Celcius).
     * Returns 1 on success, 0 on failure. Failure means the parameters given do not match a
     *   supported camera.
     */
    int init(int width, int height, float dmul, int range);

    /* Always check that frame is large enough first. */
    void update(uint16_t *frame); /* For when table is not needed. */
    float temp_single(uint16_t x); /* Does not need table, does use values from update(). */

    /* Generate lookup table for converting to Celcius, calls update().
     * For realtime use I suggest using it only when the shutter closes as a calibration step, as
     *   it is not a very fast function.
     */
    void update_table(uint16_t *frame);

    /* Table based functions, much faster than temp_single() if you need to convert many values.
     * They only work after calling update_table(), obviously.
     */
    void temp(uint16_t *frame, float *output); /* Convert an entire frame. */
    void temp(uint16_t *input, float *output, size_t len);
    inline float temp(uint16_t val) {
        return table[val & table_mask]; /* For big-endian systems this may need changing. */
    }

    /* Read correction, temp_reflected, temp_air, humidity, emissivity and distance from stored
     *   values on the camera. They are written with ABS_ZOOM command.
     */
    void readParams(uint16_t *frame);

    /* The strings are 16 bytes, no guarantee of 0 termination.
     * Accepts NULL for values you don't want copied.
     */
   void readVersion(uint16_t *frame, char *product, char *serial, char *fw_version);
};

#endif /* __INFICAM_H__ */
