#include "InfiFrame.h"

#include <cstdint>
#include <cstdlib> /* NULL */
#include <cstring> /* memcpy() */

static const float zeroc = 273.15;

/* For big endian systems this may need modification, also temp() in header. */
static inline uint16_t read_u16(uint16_t *src, int offset) {
	return src[offset];
}

static inline float read_float(uint16_t *src, int offset) {
	float ret;
	memcpy(&ret, src + offset, sizeof(ret));
	return ret;
}

/* I find it strange the equations wvc and atmt are based on work with degrees Celsius without
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

int InfiFrame::init(int width, int height) {
	this->width = width;
	this->height = height - 4;
	s1_offset = width * (height - 4);

	cal_00_offset = 390.0;
	cal_00_fpamul = 7.05;

	/* NOTE Decompiled libthermometry.so from the android lib handles 400C range for 640px wide
	 *   cameras differently, having different offsets for calibration values in that mode.
	 * I couldn't wrap my head around how that worked so it's not implemented here.
	 */
	switch (width) {
		case 640:
			fpa_off = 6867;
			fpa_div = 33.8;
			s2_offset = s1_offset + width * 3;
			break;
		case 384:
			fpa_off = 7800;
			fpa_div = 36.0;
			s2_offset = s1_offset + width * 3;
			break;
		case 256:
			fpa_off = 8617;
			fpa_div = 37.682;
			s2_offset = s1_offset + width;
			cal_00_offset = 170.0;
			cal_00_fpamul = 0.0;
			break;
		case 240:
			fpa_off = 7800;
			fpa_div = 36.0;
			s2_offset = s1_offset + width;
			break;
		default:
			return 1;
	}

	if (height < 4)
		return 2;
	return 0;
}

void InfiFrame::update(uint16_t *frame) {
	fpa_average = read_u16(frame + s1_offset, 0);
	uint16_t temp_fpa_raw = read_u16(frame + s1_offset, 1);
	temp_fpa = 20.0 - ((float) (temp_fpa_raw - fpa_off)) / fpa_div;
	temp_shutter = read_u16(frame + s2_offset, 1) / 10.0 - zeroc;
	temp_core = read_u16(frame + s2_offset, 2) / 10.0 - zeroc;
	float cal_00 = read_u16(frame + s2_offset, 0);
	cal_01 = read_float(frame + s2_offset, 3);
	float cal_02 = read_float(frame + s2_offset, 5);
	float cal_03 = read_float(frame + s2_offset, 7);
	float cal_04 = read_float(frame + s2_offset, 9);
	float cal_05 = read_float(frame + s2_offset, 11);
	temp_max_x = read_u16(frame + s1_offset, 2);
	temp_max_y = read_u16(frame + s1_offset, 3);
	temp_max = read_u16(frame + s1_offset, 4);
	temp_min_x = read_u16(frame + s1_offset, 5);
	temp_min_y = read_u16(frame + s1_offset, 6);
	temp_min = read_u16(frame + s1_offset, 7);
	temp_avg = read_u16(frame + s1_offset, 8);
	temp_center = read_u16(frame + s1_offset, 12);
	temp_user[0] = read_u16(frame + s1_offset, 13);
	temp_user[1] = read_u16(frame + s1_offset, 14);
	temp_user[2] = read_u16(frame + s1_offset, 15);

	distance_adjusted = ((distance >= 20.0) ? 20.0 : distance) * distance_multiplier;
	float atm = atmt(humidity, temp_air, distance_adjusted);
	numerator_sub = (1.0 - emissivity) * atm * powf(temp_reflected + zeroc, 4) +
					(1.0 - atm) * powf(temp_air + zeroc, 4);
	denominator = emissivity * atm;

	float ts = temp_shutter + offset_temp_shutter;
	float tfpa = temp_fpa + offset_temp_fpa;

	cal_a = cal_02 / (cal_01 + cal_01);
	cal_b = cal_02 * cal_02 / (cal_01 * cal_01 * 4.0);
	cal_c = cal_01 * powf(ts, 2) + ts * cal_02;
	cal_d = cal_03 * powf(tfpa, 2) + cal_04 * tfpa + cal_05;

	int cal_00_corr = 0;
	if (range == 120)
		cal_00_corr = roundf(cal_00_offset - tfpa * cal_00_fpamul);
	table_offset = cal_00 - ((cal_00_corr > 0) ? cal_00_corr : 0);
}

float InfiFrame::temp_single(uint16_t x) {
	/* Temperatures below what we can calculate result in sqrt of a negative number, the absolute
	 *   lowest temperature we can calculate is the one we get if we set n to 0.
	 */
	float n = sqrtf(((float) (x - table_offset) * cal_d + cal_c) / cal_01 + cal_b);
	float wtot = powf((isfinite(n) ? n : 0.0) - cal_a + zeroc, 4);
	float ttot = powf((wtot - numerator_sub) / denominator, 0.25) - zeroc;
	return ttot + (distance_adjusted * 0.85 - 1.125) * (ttot - temp_air) / 100.0 + correction;
}

void InfiFrame::update_table(uint16_t *frame) {
	update(frame);
	for (int i = 0; i < table_len; ++i)
		table[i] = temp_single(i);
}

void InfiFrame::temp(uint16_t *input, float *output) {
	temp(input, output, width * height);
}

void InfiFrame::temp(uint16_t *input, float *output, size_t len) {
	for (size_t i = 0; i < len; ++i)
		output[i] = temp(input[i]);
}

void InfiFrame::read_params(uint16_t *frame) {
	/* Presumeably this is just a 128 byte ram+eeprom area. */
	correction = read_float(frame + s2_offset, 127);
	temp_reflected = read_float(frame + s2_offset, 129);
	temp_air = read_float(frame + s2_offset, 131);
	humidity = read_float(frame + s2_offset, 133);
	emissivity = read_float(frame + s2_offset, 135);
	/* NOTE Original Infiray software uses a uint16 for distance. */
	distance = read_float(frame + s2_offset, 137);
}

void InfiFrame::read_version(uint16_t *frame, char *product, char *serial, char *fw_version) {
	if (product != NULL)
		memcpy(product, frame + s2_offset + 40, 16);
	if (serial != NULL)
		memcpy(serial, frame + s2_offset + 32, 16);
	if (fw_version != NULL)
		memcpy(fw_version, frame + s2_offset + 24, 16);
}

void InfiFrame::palette_appy(uint16_t *input, uint32_t *output) {
	palette_appy(input, output, width * height, temp(temp_min), temp(temp_max));
}

void InfiFrame::palette_appy(uint16_t *input, uint32_t *output, size_t len) {
	palette_appy(input, output, len, temp(temp_min), temp(temp_max));
}

void InfiFrame::palette_appy(uint16_t *input, uint32_t *output, float min, float max) {
	palette_appy(input, output, width * height, min, max);
}

void InfiFrame::palette_appy(uint16_t *input, uint32_t *output, size_t len, float min, float max) {
	for (size_t i = 0; i < len; ++i) {
		float frac = (fminf(fmaxf(temp(input[i]), min), max) - min) / (max - min);
		output[i] = palette[((int) roundf(frac * (float) palette_mask)) & palette_mask];
	}
}

void InfiFrame::palette_appy(float *input, uint32_t *output) {
	palette_appy(input, output, width * height, temp(temp_min), temp(temp_max));
}

void InfiFrame::palette_appy(float *input, uint32_t *output, size_t len) {
	palette_appy(input, output, len, temp(temp_min), temp(temp_max));
}

void InfiFrame::palette_appy(float *input, uint32_t *output, float min, float max) {
	palette_appy(input, output, width * height, min, max);
}

void InfiFrame::palette_appy(float *input, uint32_t *output, size_t len, float min, float max) {
	for (size_t i = 0; i < len; ++i) {
		float frac = (fminf(fmaxf(input[i], min), max) - min) / (max - min);
		output[i] = palette[((int) roundf(frac * (float) palette_mask)) & palette_mask];
	}
}
