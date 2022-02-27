#include "InfiCam.h"

#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdlib> /* NULL */

extern "C" {

InfiCam cam;
ANativeWindow *window = NULL;

// transfer specific frame data to the Surface(ANativeWindow)
int copyToSurface(uint8_t *frameData, ANativeWindow *window) {
    int result = 0;
    ANativeWindow_Buffer buffer;

    if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
        // source = frame data, destination = Surface(ANativeWindow)
        const uint8_t *src = frameData;
        const int src_w = cam.infi.width * 4;
        const int dst_w = buffer.width * 4;
        const int dst_step = buffer.stride * 4;

        // set w and h to be the smallest of the two rectangles
        const int w = src_w < dst_w ? src_w : dst_w;
        const int h = cam.infi.height < buffer.height ? cam.infi.height : buffer.height;

        // transfer from frame data to the Surface
        uint8_t *dst = (uint8_t *) buffer.bits;
        for (int i = 0; i < h; ++i) {
            memcpy(dst, src, w);
            dst += dst_step;
            src += src_w;
        }

        ANativeWindow_unlockAndPost(window);
    } else {
        result = -1;
    }

    return result;
}

void frame_callback(InfiCam *cam, uint32_t *rgb, float *temp, uint16_t *raw, void *user_ptr) {
    if (window)
        copyToSurface((uint8_t *) rgb, window);
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_connect(JNIEnv *env, jclass cl, jint fd) {
    return cam.connect(fd);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_disconnect(JNIEnv *env, jclass cl) {
    cam.disconnect();
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_nativeStartStream(JNIEnv *env, jclass cl, jobject surface) {
    if (surface == NULL)
        return 1;
    if (window != NULL)
        ANativeWindow_release(window);
    window = ANativeWindow_fromSurface(env, surface);
    if (window == NULL)
        return 2;
    // TODO we must release the window too, when, how? maybe we should have the functions not necessarily be static also
    ANativeWindow_setBuffersGeometry(window, cam.infi.width, cam.infi.height, WINDOW_FORMAT_RGBX_8888);
    if (cam.stream_start(frame_callback, NULL)) {
        ANativeWindow_release(window);
        return 3;
    }
    return 0;
}

JNIEXPORT void Java_com_serenegiant_InfiCam_nativeStopStream(JNIEnv *env, jclass cl) {
    if (window != NULL)
        ANativeWindow_release(window);
    window = NULL;
    cam.stream_stop();
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setRange(JNIEnv *env, jclass cl, jint range) {
    cam.set_range(range);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setDistanceMultiplier(JNIEnv *env, jclass cl, jfloat dm) {
    cam.set_distance_multiplier(dm);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setCorrection(JNIEnv *env, jclass cl, jfloat val) {
    cam.set_correction(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setTempReflected(JNIEnv *env, jclass cl, jfloat val) {
    cam.set_temp_reflected(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setTempAir(JNIEnv *env, jclass cl, jfloat val) {
    cam.set_temp_air(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setHumidity(JNIEnv *env, jclass cl, jfloat val) {
    cam.set_humidity(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setEmissivity(JNIEnv *env, jclass cl, jfloat val) {
    cam.set_emissivity(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setDistance(JNIEnv *env, jclass cl, jfloat val) {
    cam.set_distance(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setParams(JNIEnv *env, jclass cl, jfloat corr,
                                                      jfloat t_ref, jfloat t_air, jfloat humi,
                                                      jfloat emi, jfloat dist) {
    cam.set_params(corr, t_ref, t_air, humi, emi, dist);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_storeParams(JNIEnv *env, jclass cl) {
    cam.store_params();
}

JNIEXPORT void Java_com_serenegiant_InfiCam_updateTable(JNIEnv *env, jclass cl) {
    cam.update_table();
}

JNIEXPORT void Java_com_serenegiant_InfiCam_calibrate(JNIEnv *env, jclass cl) {
    cam.calibrate();
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_nativeSetPalette(JNIEnv *env, jclass cl, jintArray palette) {
    if (env->GetArrayLength(palette) < cam.palette_len)
        return 1;
    jint *arr = env->GetIntArrayElements(palette, NULL);
    if (arr == NULL)
        return 2;
    cam.set_palette((uint32_t *) arr);
    env->ReleaseIntArrayElements(palette, arr, JNI_ABORT);
    return 0;
}

} /* extern "C" */
