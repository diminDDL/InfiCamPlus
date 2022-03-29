#include "InfiCam.h"
#include "UVCDevice.h"
#include "InfiFrame.h"

#include <cstdint>
#include <pthread.h>
#include <cstdlib> /* NULL */
#include <cstring> /* memcpy() */
#include <cmath> /* isnan() */

void InfiCam::uvc_callback(uvc_frame_t *frame, void *user_ptr) {
	InfiCam *p = (InfiCam *) user_ptr;
	if (frame->data_bytes < p->dev.width * p->dev.height * 2)
		return;
	pthread_mutex_lock(&p->frame_callback_mutex);

	p->infi.read_params((uint16_t *) frame->data);
	if (p->table_invalid) {
		p->infi.update_table((uint16_t *) frame->data);
		p->table_invalid = 0;
	} else p->infi.update((uint16_t *) frame->data);
	p->infi.temp((uint16_t *) frame->data, p->frame_temp);

	/* Unlock before the callback so if it decides to call a function that locks the this callback
	 *   we don't end up in a deadlock.
	 */
	pthread_mutex_unlock(&p->frame_callback_mutex);
	p->frame_callback(p, p->frame_temp, (uint16_t *) frame->data, p->frame_callback_arg);
}

void InfiCam::set_float(int addr, float val) {
	uint8_t *p = (uint8_t *) &val;
	dev.set_zoom_abs((((addr + 0) & 0x7F) << 8) | p[0]);
	dev.set_zoom_abs((((addr + 1) & 0x7F) << 8) | p[1]);
	dev.set_zoom_abs((((addr + 2) & 0x7F) << 8) | p[2]);
	dev.set_zoom_abs((((addr + 3) & 0x7F) << 8) | p[3]);
}

InfiCam::~InfiCam() {
	dev.disconnect();
}

int InfiCam::connect(int fd) {
	disconnect();
	/* We initialize the mutex here because we can't use exceptions with JNI and the constructor
	 *   thus isn't able to fail.
	 */
	if (pthread_mutex_init(&frame_callback_mutex, NULL))
		return 1;
	if (dev.connect(fd)) {
		pthread_mutex_destroy(&frame_callback_mutex);
		return 2;
	}
	if (infi.init(dev.width, dev.height)) {
		dev.disconnect();
		pthread_mutex_destroy(&frame_callback_mutex);
		return 3;
	}
	dev.set_zoom_abs(CMD_MODE_TEMP);
	connected = 1;
	set_range(infi.range);
	return 0;
}

void InfiCam::disconnect() {
	if (connected) {
		stream_stop();
		dev.disconnect();
		pthread_mutex_destroy(&frame_callback_mutex);
		connected = 0;
	}
}

int InfiCam::stream_start(frame_callback_t *cb, void *user_ptr) {
	if (streaming)
		return 1;
	frame_temp = (float *) calloc(infi.width * infi.height, sizeof(float));
	if (frame_temp == NULL) {
		stream_stop();
		return 2;
	}
	frame_callback = cb;
	frame_callback_arg = user_ptr;
	table_invalid = 1;
	if (dev.stream_start(uvc_callback, this)) {
		stream_stop();
		return 3;
	}
	streaming = 1;
	return 0;
}

void InfiCam::stream_stop() {
	dev.stream_stop();
	free(frame_temp);
	frame_temp = NULL;
	streaming = 0;
}

void InfiCam::set_range(int range) {
	if (connected) {
		pthread_mutex_lock(&frame_callback_mutex);
		infi.range = range;
		dev.set_zoom_abs((range == 400) ? CMD_RANGE_400 : CMD_RANGE_120);
		pthread_mutex_unlock(&frame_callback_mutex);
	} else infi.range = range;
}

void InfiCam::set_distance_multiplier(float dm) {
	if (connected) {
		pthread_mutex_lock(&frame_callback_mutex);
		infi.distance_multiplier = dm;
		pthread_mutex_unlock(&frame_callback_mutex);
	} else infi.distance_multiplier = dm;
}

void InfiCam::set_correction(float corr) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_CORRECTION, corr);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_temp_reflected(float t_ref) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_TEMP_REFLECTED, t_ref);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_temp_air(float t_air) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_TEMP_AIR, t_air);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_humidity(float humi) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_HUMIDITY, humi);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_emissivity(float emi) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_EMISSIVITY, emi);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_distance(float dist) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_DISTANCE, dist);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_CORRECTION, corr);
	set_float(ADDR_TEMP_REFLECTED, t_ref);
	set_float(ADDR_TEMP_AIR, t_air);
	set_float(ADDR_HUMIDITY, humi);
	set_float(ADDR_EMISSIVITY, emi);
	set_float(ADDR_DISTANCE, dist);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::store_params() {
	dev.set_zoom_abs(CMD_STORE);
}

void InfiCam::update_table() {
	table_invalid = 1;
}

void InfiCam::calibrate() {
	if (!streaming)
		return;
	dev.set_zoom_abs(CMD_SHUTTER);
	pthread_mutex_lock(&frame_callback_mutex);
	update_table(); /* Xtherm (sometimes) does this 100ms before CMD_SHUTTER, but why? */
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_palette(uint32_t *palette) {
	if (streaming)
		pthread_mutex_lock(&frame_callback_mutex);
	memcpy(infi.palette, palette, palette_len * sizeof(uint32_t));
	if (streaming)
		pthread_mutex_unlock(&frame_callback_mutex);
}
