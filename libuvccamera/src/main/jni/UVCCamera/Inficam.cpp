#include "Inficam.h"

#include <stdint.h>
#include <string.h> /* memcpy() */

static const float zeroc = 273.15;

/* I find it strange the equations wvc and atmt are based on work with degrees Celcius without
 *   seemingly involving Kelvin at all. I've verified they match ht301_hacklib and ht301_ircam's
 *   implementations and the graphs in https://doi.org/10.3390/s17081718
 */

/* Water vapor coefficient from humidity and ambient temperature. */
static inline float wvc(float h, float t_atm) {
    float h1 = 1.5587, h2 = 0.06939, h3 = -2.7816e-4, h4 = 6.8455e-7;
    return h * expf(h1 + h2 * t_atm + h3 * powf(t_atm, 2) + h4 * powf(t_atm, 3));
}

/* Transmittance of the atmosphere from humitity, ambient temperature and distance. */
static inline float atmt(float h, float t_atm, float d) {
    float k_atm = 1.9, nsqd = -sqrtf(d), sqw = sqrtf(wvc(h, t_atm));
    float a1 = 0.006569, a2 = 0.01262; /* Athmospheric attenuation without water vapor. */
    float b1 = -0.002276, b2 = -0.00667; /* Attenuation for water vapor. */
    return k_atm * expf(nsqd * (a1 + b1 * sqw)) + (1.0 - k_atm) * expf(nsqd * (a2 + b2 * sqw));
}

/* If endianness conversion is necessary, this is the place to be. */
static inline uint16_t read_u16(uint16_t *src, int offset) {
    return src[offset];
}

static inline float read_float(uint16_t *src, int offset) {
    float ret;
    memcpy(&ret, src + offset, sizeof(ret));
    return ret;
}

void Inficam::init(int width, int height, int lens, int range) {
    this->width = width;
    this->height = height;
    this->lens = lens;
    this->range = range;
    tbl1_offset = width * (height - 4);
    switch (width) {
        case 640:
            fpa_off = 6867;
            fpa_div = 33.8;
            tbl2_offset = tbl1_offset + width * 3;
            break;
        case 384:
            fpa_off = 7800;
            fpa_div = 36.0;
            tbl2_offset = tbl1_offset + width * 3;
            break;
        case 256:
            fpa_off = 8617;
            fpa_div = 37.682;
            tbl2_offset = tbl1_offset + width;
            break;
        case 240:
            fpa_off = 7800;
            fpa_div = 36.0;
            tbl2_offset = tbl1_offset + width;
            break;
        default:
            // TODO throw an error
            break;
    }
    if (height < 4) {
        // TODO throw an error
    }
}

void Inficam::update(uint16_t *frame) {
    //uint16_t temp_fpa_average_raw = read_u16(frame + tbl1_offset, 0);
    uint16_t temp_fpa_raw = read_u16(frame + tbl1_offset, 1);
    temp_fpa = 20.0 - ((float) (temp_fpa_raw - fpa_off)) / fpa_div;
    temp_shutter = read_u16(frame + tbl2_offset, 1) / 10.0 - zeroc;
    temp_core = read_u16(frame + tbl2_offset, 2) / 10.0 - zeroc;
    cal_00 = read_u16(frame + tbl2_offset, 0);
    cal_01 = read_float(frame + tbl2_offset, 3);
    cal_02 = read_float(frame + tbl2_offset, 5);
    cal_03 = read_float(frame + tbl2_offset, 7);
    cal_04 = read_float(frame + tbl2_offset, 9);
    cal_05 = read_float(frame + tbl2_offset, 11);
    temp_max_x = read_u16(frame + tbl1_offset, 2);
    temp_max_y = read_u16(frame + tbl1_offset, 3);
    temp_max = read_u16(frame + tbl1_offset, 4);
    temp_min_x = read_u16(frame + tbl1_offset, 5);
    temp_min_y = read_u16(frame + tbl1_offset, 6);
    temp_min = read_u16(frame + tbl1_offset, 7);
    temp_avg = read_u16(frame + tbl1_offset, 8);
    temp_center = read_u16(frame + tbl1_offset, 12);
    temp_user[0] = read_u16(frame + tbl1_offset, 13);
    temp_user[1] = read_u16(frame + tbl1_offset, 14);
    temp_user[2] = read_u16(frame + tbl1_offset, 15);
}

void Inficam::update_table(uint16_t *frame) {
    float atm = atmt(humidity, temp_air, distance);
    float dividend = (1.0 - emissivity) * atm * powf(temp_reflected + zeroc, 4) +
            (1.0 - atm) * powf(temp_air + zeroc, 4);
    float divisor = emissivity * atm;

    float ts = temp_shutter + offset_temp_shutter;
    float tfpa = temp_fpa + offset_temp_fpa;

    float cal_a = cal_02 / (cal_01 + cal_01);
    float cal_b = cal_02 * cal_02 / (cal_01 * cal_01 * 4.0);
    float cal_c = cal_01 * powf(ts, 2) + ts * cal_02;
    float cal_d = cal_03 * powf(tfpa, 2) + cal_04 * tfpa + cal_05;

    // TODO TODO TODO
    /*if (type_) // TODO
        v2 = 0; // TODO
    else v2 = (signed int) (390.0 - fpatmp_ * 7.05);*/ // TODO
    int v2 = 0xaa; // for 256px wide camera
    int v19 = cal_00 - v2; // cx is a variable from camera

    for (int i = 0; i < table_len; ++i) {
        float v20 = sqrtf(((float) (i - v19) * cal_d + cal_c) / cal_01 + cal_b) - cal_a + zeroc;
        float wtot = powf(v20, 4);

        float v18 = powf((wtot - dividend) / divisor, 0.25) - zeroc;
        float v21 = (distance >= 20) ? 20 : distance; // TODO depend on lens
        table[i] = v18 + ((float) v21 * 0.85 - 1.125) * (v18 - temp_air) / 100.0;
    }
}
