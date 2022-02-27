#include "InfiCam.h"

#include <string.h> /* memcpy() */

void InfiCam::uvc_callback(uvc_frame_t *frame, void *user_ptr) {
    InfiCam *p = (InfiCam *) user_ptr;
    if (frame->data_bytes < p->dev.width * p->dev.height * 2)
        return;
    pthread_mutex_lock(&p->frame_callback_mutex);

    if (p->update_table) {
        p->infi.read_params((uint16_t *) frame->data);
        p->infi.update_table((uint16_t *) frame->data);
        p->update_table = 0;
    } else p->infi.update((uint16_t *) frame->data);
    p->infi.temp((uint16_t *) frame->data, p->frame_temp);
    p->infi.palette_appy(p->frame_temp, p->frame_rgb);
    p->frame_callback(p, p->frame_rgb, p->frame_temp, (uint16_t *) frame->data,
                      p->frame_callback_arg);

    pthread_mutex_unlock(&p->frame_callback_mutex);
}

void InfiCam::set_float(int addr, float val) {
    uint8_t *p = (uint8_t *) &val;
    dev.set_zoom_abs((((addr + 0) & 0x7F) << 8) | p[0]);
    dev.set_zoom_abs((((addr + 1) & 0x7F) << 8) | p[1]);
    dev.set_zoom_abs((((addr + 2) & 0x7F) << 8) | p[2]);
    dev.set_zoom_abs((((addr + 3) & 0x7F) << 8) | p[3]);
}

InfiCam::~InfiCam() {
    stream_stop();
    dev.disconnect();
}

int InfiCam::connect(int fd) {
    if (pthread_mutex_init(&frame_callback_mutex, NULL))
        return 1;
    if (dev.connect(fd))
        return 2;
    infi.init(dev.width, dev.height, 1.0, 120); // TODO the dmul and range values
    dev.set_zoom_abs(CMD_MODE_TEMP);
    return 0;
}

void InfiCam::disconnect() {
    stream_stop();
    dev.disconnect();
    pthread_mutex_destroy(&frame_callback_mutex);
}

int InfiCam::stream_start(frame_callback_t *cb, void *user_ptr) {
    frame_rgb = (uint32_t *) calloc(infi.width * infi.height, sizeof(uint32_t));
    frame_temp = (float *) calloc(infi.width * infi.height, sizeof(float));
    if (frame_rgb == NULL || frame_temp == NULL) {
        free(frame_rgb);
        free(frame_temp);
        return 1;
    }
    frame_callback = cb;
    frame_callback_arg = user_ptr;
    if (dev.stream_start(uvc_callback, this)) {
        free(frame_rgb);
        free(frame_temp);
        return 2;
    }
    streaming = 1;
    return 0;
}

void InfiCam::stream_stop() {
    dev.stream_stop();
    free(frame_rgb);
    free(frame_temp);
    frame_rgb = NULL;
    frame_temp = NULL;
    streaming = 0;
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

void InfiCam::set_palette(uint32_t *palette) {
    memcpy(infi.palette, palette, palette_len * sizeof(uint32_t));
}

void InfiCam::calibrate() {
    if (!streaming)
        return;
    dev.set_zoom_abs(CMD_SHUTTER);
    pthread_mutex_lock(&frame_callback_mutex);
    update_table = 1; /* Xtherm (sometimes) does this 100ms before CMD_SHUTTER, but why? */
    pthread_mutex_unlock(&frame_callback_mutex);
}
