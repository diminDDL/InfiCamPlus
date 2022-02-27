#ifndef __INFICAM_H__
#define __INFICAM_H__

#include "UVCDevice.h"
#include "InfiFrame.h"
#include <cstdint>

typedef void (inficam_frame_callback_t)(uint32_t *rgb, float *temp, uint16_t *raw, void *user_ptr);

class InfiCam {
    inficam_frame_callback_t *frame_callback;
    void *frame_callback_arg;
    uint32_t *frame_rgb;
    float *frame_temp;

    static void uvc_callback(uvc_frame_t *frame, void *user_ptr);

public:
    UVCDevice dev; // TODO these should probably be private
    InfiFrame infi;

    ~InfiCam();

    int connect(int fd);
    void disconnect();
    int stream_start(inficam_frame_callback_t *cb, void *user_ptr);
    void stream_stop();
};

#endif /* __INFICAM_H__ */
