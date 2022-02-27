#include "InfiCam.h"

#include <string.h> /* memcpy() */

void InfiCam::uvc_callback(uvc_frame_t *frame, void *user_ptr) {
    InfiCam *p = (InfiCam *) user_ptr;
    if (frame->data_bytes < p->dev.width * p->dev.height * 2)
        return;
    pthread_mutex_lock(&p->frame_callback_mutex);

    if (p->update_table) {
        p->infi.read_params((uint16_t *) frame->data); // TODO temporary?
        p->infi.update_table((uint16_t *) frame->data);
        p->update_table = 0;
    } else p->infi.update((uint16_t *) frame->data);
    p->infi.temp((uint16_t *) frame->data, p->frame_temp);
    p->infi.palette_appy(p->frame_temp, p->frame_rgb);
    p->frame_callback(p, p->frame_rgb, p->frame_temp, (uint16_t *) frame->data,
                      p->frame_callback_arg);

    pthread_mutex_unlock(&p->frame_callback_mutex);
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

void InfiCam::set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist) {
    set_params(corr, t_ref, t_air, humi, emi, dist, 0.0, 0.0);
}

void InfiCam::set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist,
                         float off_fpa, float off_shut) {
    if (streaming)
        pthread_mutex_lock(&frame_callback_mutex);
    infi.correction = corr;
    infi.temp_reflected = t_ref;
    infi.temp_air = t_air;
    infi.humidity = humi;
    infi.emissivity = emi;
    infi.distance = dist;
    infi.offset_temp_fpa = off_fpa;
    infi.offset_temp_shutter = off_shut;
    if (streaming)
        pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_float(int addr, float val) {
    uint8_t *p = (uint8_t *) &val;
    dev.set_zoom_abs((((addr + 0) & 0x7F) << 8) | p[0]);
    dev.set_zoom_abs((((addr + 1) & 0x7F) << 8) | p[1]);
    dev.set_zoom_abs((((addr + 2) & 0x7F) << 8) | p[2]);
    dev.set_zoom_abs((((addr + 3) & 0x7F) << 8) | p[3]);
}

void InfiCam::set_u16(int addr, uint16_t val) {
    dev.set_zoom_abs((((addr + 0) & 0x7F) << 8) | ((val >> 0) & 0xFF));
    dev.set_zoom_abs((((addr + 1) & 0x7F) << 8) | ((val >> 8) & 0xFF));
}

void InfiCam::store_params() {
    // TODO when to write the params to the camera etc?
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
