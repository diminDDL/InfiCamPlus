#ifndef __INFICAM_H__
#define __INFICAM_H__

#include "UVCDevice.h"
#include "InfiFrame.h"
#include <cstdint>
#include <pthread.h>

class InfiCam {
    typedef void (frame_callback_t)(InfiCam *cam, uint32_t *rgb, float *temp, uint16_t *raw,
                void *user_ptr);

    UVCDevice dev;
    frame_callback_t *frame_callback;
    void *frame_callback_arg;
    uint32_t *frame_rgb;
    float *frame_temp;
    pthread_mutex_t frame_callback_mutex;
    int streaming = 0, update_table = 0;

    static const int CMD_SHUTTER = 0x8000;
    static const int CMD_MODE_TEMP = 0x8004;
    static const int CMD_MODE_YUV = 0x8005;
    static const int CMD_RANGE_120 = 0x8020;
    static const int CMD_RANGE_400 = 0x8021;
    static const int CMD_STORE = 0x80FF;

    static void uvc_callback(uvc_frame_t *frame, void *user_ptr);

public:
    static const int palette_len = InfiFrame::palette_len;
    InfiFrame infi; /* Updated before each stream CB with info relevant to the frame. */

    ~InfiCam();

    int connect(int fd);
    void disconnect();
    int stream_start(frame_callback_t *cb, void *user_ptr); /* CB arguments valid until return. */
    void stream_stop();

    void set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist);
    void set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist,
                    float off_fpa, float off_shut);

    void set_float(int addr, float val); /* Write to camera user memory. */
    void set_u16(int addr, uint16_t val);
    void store_params(); /* Store user memory to camera so values remain when reconnecting. */

    void set_palette(uint32_t *palette);

    void calibrate();
};

#endif /* __INFICAM_H__ */
