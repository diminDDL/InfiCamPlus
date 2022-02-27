#include "InfiCam.h"

#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdlib> /* NULL */

extern "C" {

InfiCam cam;
ANativeWindow *window = NULL;

void frame_callback(InfiCam *cam, uint32_t *rgb, float *temp, uint16_t *raw, void *user_ptr) {
    if (window == NULL)
        return;
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
        // source = frame data, destination = Surface(ANativeWindow)
        const uint8_t *src = (uint8_t *) rgb;
        const int src_w = cam->infi.width * 4;
        const int dst_w = buffer.width * 4;
        const int dst_step = buffer.stride * 4;

        // set w and h to be the smallest of the two rectangles
        const int w = src_w < dst_w ? src_w : dst_w;
        const int h = cam->infi.height < buffer.height ? cam->infi.height : buffer.height;

        // transfer from frame data to the Surface
        uint8_t *dst = (uint8_t *) buffer.bits;
        for (int i = 0; i < h; ++i) {
            memcpy(dst, src, w);
            dst += dst_step;
            src += src_w;
        }

        ANativeWindow_unlockAndPost(window);
    }
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_connect(JNIEnv *env, jclass cl, jint fd) {
    return cam.connect(fd);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_disconnect(JNIEnv *env, jclass cl) {
    cam.disconnect();
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_startStream(JNIEnv *env, jclass cl, jobject surface) {

    // TODO!!
    uint32_t palette[InfiCam::palette_len];
    for (int i = 0; i + 4 <= sizeof(palette); i += 4) {
        double x = (double) i / (double) sizeof(palette);
        ((uint8_t *) palette)[i + 0] = round(255 * sqrt(x));
        ((uint8_t *) palette)[i + 1] = round(255 * pow(x, 3));
        ((uint8_t *) palette)[i + 2] = round(255 * fmax(0, sin(2 * M_PI * x)));
        ((uint8_t *) palette)[i + 3] = 255;
    }
    cam.set_palette(palette);

    // TODO (netman) This is temporary generating a palette thing, dunno what the plan is yet, but it doesn't belong here.
    // TODO add partial (0-270 degrees) rainbow, where cold is blue and red is hot
    /*for (int i = 0; i + 4 <= sizeof(ic.palette); i += 4) {
        double h = 360.0 - (double) i / (double) sizeof(ic.palette) * 360.0;
        double x = (1 - abs(fmod(h / 60.0, 2) - 1));
        double r, g, b;
        if (h >= 0 && h < 60)
            r = 1, g = x, b = 0;
        else if(h >= 60 && h < 120)
            r = x, g = 1, b = 0;
        else if(h >= 120 && h < 180)
            r = 0, g = 1, b = x;
        else if(h >= 180 && h < 240)
            r = 0, g = x, b = 1;
        else if(h >= 240 && h < 300)
            r = x, g = 0, b = 1;
        else r = 1, g = 0, b = x;
        ((uint8_t *) ic.palette)[i + 0] = round(255 * r);
        ((uint8_t *) ic.palette)[i + 1] = round(255 * g);
        ((uint8_t *) ic.palette)[i + 2] = round(255 * b);
        ((uint8_t *) ic.palette)[i + 3] = 255;
    }*/

    /* TODO note to self the callback thing went as follows:

        savedVm->AttachCurrentThread(&env, NULL);

		float *temperatureData = p->mCbTemper;
		....

		jfloatArray mNCbTemper = env->NewFloatArray(p->cam.infi.width*p->cam.infi.height+10);
		env->SetFloatArrayRegion(mNCbTemper, 0, 10+p->cam.infi.width*p->cam.infi.height, p->mCbTemper);
		if (p->mTemperatureCallbackObj != NULL) {
			env->CallVoidMethod(p->mTemperatureCallbackObj, p->iTemperatureCallback.onReceiveTemperature, mNCbTemper);
			env->ExceptionClear();
		}
		env->DeleteLocalRef(mNCbTemper);

		savedVm->DetachCurrentThread();
     */

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

JNIEXPORT void Java_com_serenegiant_InfiCam_stopStream(JNIEnv *env, jclass cl) {
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
