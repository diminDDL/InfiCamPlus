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

    static const int ADDR_CORRECTION = 0;
    static const int ADDR_TEMP_REFLECTED = 4;
    static const int ADDR_TEMP_AIR = 8;
    static const int ADDR_HUMIDITY = 12;
    static const int ADDR_EMISSIVITY = 16;
    static const int ADDR_DISTANCE = 20;

    static void uvc_callback(uvc_frame_t *frame, void *user_ptr);
    void set_float(int addr, float val); /* Write to camera user memory, needs lock. */

public:
    static const int palette_len = InfiFrame::palette_len;
    /* InfiFrame class gets updated before each stream CB with info relevant to the frame.
     * The width and height in there are valid after connect().
     */
    InfiFrame infi;

    ~InfiCam();

    int connect(int fd);
    void disconnect();
    int stream_start(frame_callback_t *cb, void *user_ptr); /* CB arguments valid until return. */
    void stream_stop();

    /* Setting parameters, only works while streaming. */
    void set_correction(float corr);
    void set_temp_reflected(float t_ref);
    void set_temp_air(float t_air);
    void set_humidity(float humi);
    void set_emissivity(float emi);
    void set_distance(float dist);
    void set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist);
    void store_params(); /* Store user memory to camera so values remain when reconnecting. */

    void set_palette(uint32_t *palette);

    void calibrate();
};

#endif /* __INFICAM_H__ */
