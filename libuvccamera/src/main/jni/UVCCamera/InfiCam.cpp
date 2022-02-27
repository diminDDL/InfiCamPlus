#include "InfiCam.h"

void InfiCam::uvc_callback(uvc_frame_t *frame, void *user_ptr) {
    InfiCam *p = (InfiCam *) user_ptr;
    if (frame->data_bytes < p->dev.width * p->dev.height * 2)
        return;
    // TODO update table when?
    p->infi.update((uint16_t *) frame->data);
    p->infi.palette_appy((uint16_t *) frame->data, p->frame_rgb);
    p->infi.temp((uint16_t *) frame->data, p->frame_temp);
    p->frame_callback(p->frame_rgb, p->frame_temp, (uint16_t *) frame->data, p->frame_callback_arg);
}

InfiCam::~InfiCam() {
    stream_stop();
    dev.disconnect();
}

int InfiCam::connect(int fd) {
    dev.connect(fd);
    infi.init(dev.width, dev.height, 1.0, 120); // TODO the dmul and range values
    return 0;
}

void InfiCam::disconnect() {
    stream_stop();
    dev.disconnect();
}

int InfiCam::stream_start(inficam_frame_callback_t *cb, void *user_ptr) {
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
    return 0;
}

void InfiCam::stream_stop() {
    dev.stream_stop();
    free(frame_rgb);
    free(frame_temp);
    frame_rgb = NULL;
    frame_temp = NULL;
}
