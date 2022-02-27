#ifndef __INFICAM_H__
#define __INFICAM_H__

#include "UVCDevice.h"
#include "InfiFrame.h"
#include <cstdint>
#include <pthread.h>

typedef void (inficam_frame_callback_t)(uint32_t *rgb, float *temp, uint16_t *raw, void *user_ptr);

class InfiCam {
    InfiFrame infi;
    inficam_frame_callback_t *frame_callback;
    void *frame_callback_arg;
    uint32_t *frame_rgb;
    float *frame_temp;
    pthread_mutex_t frame_callback_mutex;
    int streaming = 0, update_table = 0;

    static const int CMD_SHUTTER = 0x8000;
    static const int CMD_MODE_TEMP = 0x8004;
    static const int CMD_MODE_YUV = 0x8005;

    static void uvc_callback(uvc_frame_t *frame, void *user_ptr);

public:
    UVCDevice dev; // TODO these should probably be private
    int width = 0, height = 0;
    static const int palette_len = InfiFrame::palette_len;

    ~InfiCam();

    int connect(int fd);
    void disconnect();
    int stream_start(inficam_frame_callback_t *cb, void *user_ptr);
    void stream_stop();

    void set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist);
    void set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist,
                    float off_fpa, float off_shut);

    void set_palette(uint32_t *palette);

    void calibrate();
};

#endif /* __INFICAM_H__ */
