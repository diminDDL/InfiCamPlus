#include "InfiCam.h"

#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdlib> /* NULL */

class InfiCamJNI : public InfiCam {
public:
    ANativeWindow *window = NULL;

    ~InfiCamJNI() {
        if (window != NULL)
            ANativeWindow_release(window);
    }
};

void frame_callback(InfiCam *cam, uint32_t *rgb, float *temp, uint16_t *raw, void *user_ptr) {
    InfiCamJNI *icj = (InfiCamJNI *) cam;
    if (icj->window == NULL)
        return;
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

/* Get the InfiCamJNI class from jobject. */
static InfiCamJNI *getObject(JNIEnv *env, jobject obj) {
    jclass cls = env->GetObjectClass(obj);
    if (!cls)
        env->FatalError("GetObjectClass failed");
    jfieldID nativeObjectPointerID = env->GetFieldID(cls, "instance", "J");
    if (!nativeObjectPointerID)
        env->FatalError("GetFieldID failed");
    jlong nativeObjectPointer = env->GetLongField(obj, nativeObjectPointerID);
    return (InfiCamJNI *) nativeObjectPointer;
}

/* Set an integer variable in the Java InfiCam class. */
static void setIntVar(JNIEnv *env, jobject obj, char *name, jint value) {
    jclass cls = env->GetObjectClass(obj);
    if (!cls)
        env->FatalError("GetObjectClass failed");
    jfieldID nativeObjectPointerID = env->GetFieldID(cls, name, "I");
    if (!nativeObjectPointerID)
        env->FatalError("GetFieldID failed");
    env->SetIntField(obj, nativeObjectPointerID, value);
}

extern "C" {

JNIEXPORT jlong Java_com_serenegiant_InfiCam_nativeNew(JNIEnv *env, jclass self) {
    return (jlong) new InfiCamJNI();
}

JNIEXPORT void Java_com_serenegiant_InfiCam_nativeDelete(JNIEnv *env, jclass self, jlong ptr) {
    delete (InfiCamJNI *) ptr;
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_nativeConnect(JNIEnv *env, jobject self, jint fd) {
    InfiCamJNI *icj = getObject(env, self);
    int ret = icj->connect(fd);
    setIntVar(env, self, "width", icj->infi.width);
    setIntVar(env, self, "height", icj->infi.height);
    return ret;
}

JNIEXPORT void Java_com_serenegiant_InfiCam_disconnect(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->disconnect();
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_nativeStartStream(JNIEnv *env, jobject self, jobject surface) {
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

    /* TODO note to self the callback thing went as follows:

        savedVm->AttachCurrentThread(&env, NULL);

		float *temperatureData = p->mCbTemper;
		....

		jfloatArray mNCbTemper = env->NewFloatArray(p->icj->infi.width*p->icj->infi.height+10);
		env->SetFloatArrayRegion(mNCbTemper, 0, 10+p->icj->infi.width*p->icj->infi.height, p->mCbTemper);
		if (p->mTemperatureCallbackObj != NULL) {
			env->CallVoidMethod(p->mTemperatureCallbackObj, p->iTemperatureCallback.onReceiveTemperature, mNCbTemper);
			env->ExceptionClear();
		}
		env->DeleteLocalRef(mNCbTemper);

		savedVm->DetachCurrentThread();
     */

    if (surface == NULL)
        return 1;
    if (icj->window != NULL)
        ANativeWindow_release(icj->window);
    icj->window = ANativeWindow_fromSurface(env, surface);
    if (icj->window == NULL)
        return 2;
    // TODO we must release the window too, when, how? maybe we should have the functions not necessarily be static also
    ANativeWindow_setBuffersGeometry(icj->window, icj->infi.width, icj->infi.height, WINDOW_FORMAT_RGBX_8888);
    if (icj->stream_start(frame_callback, NULL)) {
        ANativeWindow_release(icj->window);
        return 3;
    }
    return 0;
}

JNIEXPORT void Java_com_serenegiant_InfiCam_stopStream(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    if (icj->window != NULL)
        ANativeWindow_release(icj->window);
    icj->window = NULL;
    icj->stream_stop();
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setRange(JNIEnv *env, jobject self, jint range) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_range(range);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setDistanceMultiplier(JNIEnv *env, jobject self, jfloat dm) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_distance_multiplier(dm);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setCorrection(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_correction(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setTempReflected(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_temp_reflected(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setTempAir(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_temp_air(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setHumidity(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_humidity(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setEmissivity(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_emissivity(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setDistance(JNIEnv *env, jobject self, jfloat val) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_distance(val);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_setParams(JNIEnv *env, jobject self, jfloat corr,
                                                      jfloat t_ref, jfloat t_air, jfloat humi,
                                                      jfloat emi, jfloat dist) {
    InfiCamJNI *icj = getObject(env, self);
    icj->set_params(corr, t_ref, t_air, humi, emi, dist);
}

JNIEXPORT void Java_com_serenegiant_InfiCam_storeParams(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->store_params();
}

JNIEXPORT void Java_com_serenegiant_InfiCam_updateTable(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->update_table();
}

JNIEXPORT void Java_com_serenegiant_InfiCam_calibrate(JNIEnv *env, jobject self) {
    InfiCamJNI *icj = getObject(env, self);
    icj->calibrate();
}

JNIEXPORT jint Java_com_serenegiant_InfiCam_nativeSetPalette(JNIEnv *env, jobject self, jintArray palette) {
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
