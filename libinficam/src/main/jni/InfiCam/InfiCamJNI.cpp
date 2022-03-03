#include "InfiCam.h"

#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdlib> /* NULL */

#define FRAMEINFO_TYPE "be/ntmn/InfiCam$FrameInfo"

JavaVM *javaVM = NULL;

jclass cls_FrameInfo;

extern "C" JNICALL jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    javaVM = vm;
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6))
        return JNI_ERR;
    cls_FrameInfo = env->FindClass(FRAMEINFO_TYPE);
    cls_FrameInfo = (jclass) env->NewGlobalRef(cls_FrameInfo);
    return JNI_VERSION_1_6;
}

class InfiCamJNI : public InfiCam {
public:
    ANativeWindow *window = NULL;
    jobject obj = 0;
};

/* Get the InfiCamJNI class from jobject. */
static InfiCamJNI *getObject(JNIEnv *env, jobject obj) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID nativeObjectPointerID = env->GetFieldID(cls, "instance", "J");
    return (InfiCamJNI *) env->GetLongField(obj, nativeObjectPointerID);
}

/* Set an integer variable in the a Java class. */
static void setIntVar(JNIEnv *env, jobject obj, const char *name, jint value) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID nativeObjectPointerID = env->GetFieldID(cls, name, "I");
    env->SetIntField(obj, nativeObjectPointerID, value);
}

/* Set an integer variable in the a Java class. */
static void setFloatVar(JNIEnv *env, jobject obj, const char *name, jfloat value) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID nativeObjectPointerID = env->GetFieldID(cls, name, "F");
    env->SetFloatField(obj, nativeObjectPointerID, value);
}

/* Frame callback to draw to Android Surface and call a Java callback. */
void frame_callback(InfiCam *cam, uint32_t *rgb, float *temp, uint16_t *raw, void *user_ptr) {
    InfiCamJNI *icj = (InfiCamJNI *) cam;

    /* Update the surface if we have one. */
    if (icj->window != NULL) {
        ANativeWindow_Buffer buffer;
        if (ANativeWindow_lock(icj->window, &buffer, NULL) == 0) {
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

            ANativeWindow_unlockAndPost(icj->window);
        }
    }

    if (icj->obj != 0) {
        /* Attach to Java thread. */
        JNIEnv *cenv;
        javaVM->AttachCurrentThread(&cenv, NULL);

        /* Fill the FrameInfo struct. */
        jobject fi = cenv->AllocObject(cls_FrameInfo);
        setFloatVar(cenv, fi, "max", icj->infi.temp(icj->infi.temp_max));
        setIntVar(cenv, fi, "max_x", icj->infi.temp_max_x);
        setIntVar(cenv, fi, "max_y", icj->infi.temp_max_y);
        setFloatVar(cenv, fi, "min", icj->infi.temp(icj->infi.temp_min));
        setIntVar(cenv, fi, "min_x", icj->infi.temp_min_x);
        setIntVar(cenv, fi, "min_y", icj->infi.temp_min_y);
        setFloatVar(cenv, fi, "center", icj->infi.temp(icj->infi.temp_center));
        setFloatVar(cenv, fi, "avg", icj->infi.temp(icj->infi.temp_avg));

        setFloatVar(cenv, fi, "correction", icj->infi.correction);
        setFloatVar(cenv, fi, "temp_reflected", icj->infi.temp_reflected);
        setFloatVar(cenv, fi, "temp_air", icj->infi.temp_air);
        setFloatVar(cenv, fi, "humidity", icj->infi.humidity);
        setFloatVar(cenv, fi, "emissivity", icj->infi.emissivity);
        setFloatVar(cenv, fi, "distance", icj->infi.distance);

        /* Make a Java array from the temperature array. */
        size_t temp_len = icj->infi.width * icj->infi.height;
        jfloatArray jtemp = cenv->NewFloatArray(temp_len);
        cenv->SetFloatArrayRegion(jtemp, 0, temp_len, temp);

        /* Call the callback. */
        jclass cls = cenv->GetObjectClass(icj->obj);
        jmethodID mid = cenv->GetMethodID(cls, "frameCallback", "(L" FRAMEINFO_TYPE ";[F)V");
        cenv->CallVoidMethod(icj->obj, mid, fi, jtemp);

        /* Clean up and detach. */
        cenv->DeleteLocalRef(jtemp);
        cenv->DeleteLocalRef(fi);
        javaVM->DetachCurrentThread();
    }
}

extern "C" {

JNIEXPORT jlong Java_be_ntmn_InfiCam_nativeNew(JNIEnv *env, jclass cls) {
    return (jlong) new InfiCamJNI();
}

JNIEXPORT void Java_be_ntmn_InfiCam_nativeDelete(JNIEnv *env, jclass cls, jlong ptr) {
    InfiCamJNI *icj = (InfiCamJNI *) ptr;
    if (icj->obj != 0)
        env->DeleteGlobalRef(icj->obj);
    if (icj->window != NULL)
        ANativeWindow_release(icj->window);
    delete icj;
}

JNIEXPORT jint Java_be_ntmn_InfiCam_nativeConnect(JNIEnv *env, jobject self, jint fd) {
    InfiCamJNI *icj = getObject(env, self);
    int ret = icj->connect(fd);
    return ret;
}

JNIEXPORT void Java_be_ntmn_InfiCam_disconnect(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->disconnect();
}

JNIEXPORT jint Java_be_ntmn_InfiCam_nativeStartStream(JNIEnv *env, jobject self, jobject surface) {
    InfiCamJNI *icj = getObject(env, self);

    // TODO!!
    uint32_t palette[InfiCam::palette_len];
    for (int i = 0; i + 4 <= sizeof(palette); i += 4) {
        double x = (double) i / (double) sizeof(palette);
        ((uint8_t *) palette)[i + 0] = round(255 * sqrt(x));
        ((uint8_t *) palette)[i + 1] = round(255 * pow(x, 3));
        ((uint8_t *) palette)[i + 2] = round(255 * fmax(0, sin(2 * M_PI * x)));
        ((uint8_t *) palette)[i + 3] = 255;
    }
    icj->set_palette(palette);

    if (icj->window != NULL) {
        ANativeWindow_release(icj->window);
        icj->window = NULL;
    }
    if (icj->obj != 0) {
        env->DeleteGlobalRef(icj->obj);
        icj->obj = 0;
    }

    if (surface != NULL) {
        icj->window = ANativeWindow_fromSurface(env, surface);
        if (icj->window == NULL)
            return 2;
        ANativeWindow_setBuffersGeometry(icj->window, icj->infi.width, icj->infi.height,
                                         WINDOW_FORMAT_RGBX_8888);
    }

    icj->obj = env->NewGlobalRef(self);
    if (icj->stream_start(frame_callback, NULL)) {
        if (icj->window != NULL)
            ANativeWindow_release(icj->window);
        icj->window = NULL;
        if (icj->obj != 0)
            env->DeleteGlobalRef(icj->obj);
        icj->obj = 0;
        return 3;
    }
    return 0;
}

JNIEXPORT void Java_be_ntmn_InfiCam_stopStream(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    if (icj->window != NULL)
        ANativeWindow_release(icj->window);
    icj->window = NULL;
    if (icj->obj != 0)
        env->DeleteGlobalRef(icj->obj);
    icj->obj = 0;
    icj->stream_stop();
}

JNIEXPORT void Java_be_ntmn_InfiCam_setRange(JNIEnv *env, jobject self, jint range) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_range(range);
}

JNIEXPORT jint Java_be_ntmn_InfiCam_getWidth(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    return icj->infi.width;
}

JNIEXPORT jint Java_be_ntmn_InfiCam_getHeight(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    return icj->infi.height;
}

JNIEXPORT void Java_be_ntmn_InfiCam_setDistanceMultiplier(JNIEnv *env, jobject self, jfloat dm) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_distance_multiplier(dm);
}

JNIEXPORT void Java_be_ntmn_InfiCam_setCorrection(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_correction(val);
}

JNIEXPORT void Java_be_ntmn_InfiCam_setTempReflected(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_temp_reflected(val);
}

JNIEXPORT void Java_be_ntmn_InfiCam_setTempAir(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_temp_air(val);
}

JNIEXPORT void Java_be_ntmn_InfiCam_setHumidity(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_humidity(val);
}

JNIEXPORT void Java_be_ntmn_InfiCam_setEmissivity(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_emissivity(val);
}

JNIEXPORT void Java_be_ntmn_InfiCam_setDistance(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_distance(val);
}

JNIEXPORT void Java_be_ntmn_InfiCam_setParams(JNIEnv *env, jobject self, jfloat corr,
                                                      jfloat t_ref, jfloat t_air, jfloat humi,
                                                      jfloat emi, jfloat dist) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_params(corr, t_ref, t_air, humi, emi, dist);
}

JNIEXPORT void Java_be_ntmn_InfiCam_storeParams(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->store_params();
}

JNIEXPORT void Java_be_ntmn_InfiCam_updateTable(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->update_table();
}

JNIEXPORT void Java_be_ntmn_InfiCam_calibrate(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->calibrate();
}

JNIEXPORT jint Java_be_ntmn_InfiCam_nativeSetPalette(JNIEnv *env, jobject self, jintArray palette) {
    InfiCamJNI *icj = getObject(env, self);
    if (env->GetArrayLength(palette) < icj->palette_len)
        return 1;
    jint *arr = env->GetIntArrayElements(palette, NULL);
    if (arr == NULL)
        return 2;
    icj->set_palette((uint32_t *) arr);
    env->ReleaseIntArrayElements(palette, arr, JNI_ABORT);
    return 0;
}

} /* extern "C" */
