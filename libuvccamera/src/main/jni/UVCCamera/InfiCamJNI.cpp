#include "InfiCam.h"

#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdlib> /* NULL */

extern "C" {

InfiCam cam;

JNIEXPORT jint Java_com_serenegiant_InfiCam_connect(JNIEnv *env, jclass cl, jint fd) {
    return cam.connect(fd);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_disconnect(JNIEnv *env, jclass cl) {
    cam.disconnect();
}

// TODO start stream

JNIEXPORT void Java_com_serenegiant_InfiCam_nativeStopStream(JNIEnv *env, jclass cl) {
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
