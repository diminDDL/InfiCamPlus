#include "InfiCam.h"

#include <jni.h>
#include <android/native_window_jni.h>
#include <cstdlib> /* NULL */
#include <pthread.h>

#define FRAMEINFO_TYPE "be/ntmn/libinficam/InfiCam$FrameInfo"

JavaVM *javaVM = NULL;

extern "C" JNICALL jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	javaVM = vm;
	JNIEnv *env;
	if (vm->GetEnv((void **) &env, JNI_VERSION_1_6))
		return JNI_ERR;
	return JNI_VERSION_1_6;
}

class InfiCamJNI : public InfiCam {
public:
	JNIEnv *env;
	jobject obj;
	int jthread_stop = 0;
	uint32_t *rgb = NULL;
	float *temp = NULL;
	uint16_t *raw = NULL;

	/* Initialized elsewhere to avoid needing exceptions. */
	pthread_t jthread;
	pthread_mutex_t jthread_mutex;
	pthread_cond_t jthread_cond;
	ANativeWindow *window = NULL;

	InfiCamJNI(JNIEnv *env, jobject obj) {
		this->env = env;
		this->obj = env->NewGlobalRef(obj);
	}

	~InfiCamJNI() {
		env->DeleteGlobalRef(obj);
	}
};

/* Get the InfiCamJNI class from jobject. */
static InfiCamJNI *getObject(JNIEnv *env, jobject obj) {
	jclass cls = env->GetObjectClass(obj);
	jfieldID nativeObjectPointerID = env->GetFieldID(cls, "instance", "J");
	env->DeleteLocalRef(cls);
	return (InfiCamJNI *) env->GetLongField(obj, nativeObjectPointerID);
}

/* Set an integer variable in the a Java class. */
static void setIntVar(JNIEnv *env, jobject obj, const char *name, jint value) {
	jclass cls = env->GetObjectClass(obj);
	jfieldID nativeObjectPointerID = env->GetFieldID(cls, name, "I");
	env->SetIntField(obj, nativeObjectPointerID, value);
	env->DeleteLocalRef(cls);
}

/* Set an integer variable in the a Java class. */
static void setFloatVar(JNIEnv *env, jobject obj, const char *name, jfloat value) {
	jclass cls = env->GetObjectClass(obj);
	jfieldID nativeObjectPointerID = env->GetFieldID(cls, name, "F");
	env->SetFloatField(obj, nativeObjectPointerID, value);
	env->DeleteLocalRef(cls);
}

/* Frame callback that notifies jthread (described later). */
static void frame_callback(InfiCam *cam, float *temp, uint16_t *raw, void *user_ptr) {
	InfiCamJNI *icj = (InfiCamJNI *) cam;
	pthread_mutex_lock(&icj->jthread_mutex);
	icj->temp = temp;
	icj->raw = raw;
	pthread_cond_broadcast(&icj->jthread_cond);
	/* Now we wait for the other thread to finish and signal the same condition, this works
	 *   because only threads that are currently waiting get signaled.
	 */
	while (pthread_cond_wait(&icj->jthread_cond, &icj->jthread_mutex));
	pthread_mutex_unlock(&icj->jthread_mutex);
}

/* This thread will attach to the JVM and draw frames to an Android surface, then call a Java
 *   callback, the reason we need this is because the callback from libuvc doesn't allow us to do
 *   something at the end of the thread that calls and we need to detach the thread from the JVM
 *   when we are done.
 */
static void *jthread_run(void *a) {
	InfiCamJNI *icj = (InfiCamJNI *) a;
	JNIEnv *env;
	javaVM->AttachCurrentThread(&env, NULL);
	pthread_mutex_lock(&icj->jthread_mutex);
	while (1) {
		while (!icj->jthread_stop && pthread_cond_wait(&icj->jthread_cond, &icj->jthread_mutex));
		if (icj->jthread_stop) {
			pthread_mutex_unlock(&icj->jthread_mutex);
			break;
		}

		/* Fill the FrameInfo struct. */
		jclass cls = env->GetObjectClass(icj->obj);
		jfieldID fi_id = env->GetFieldID(cls, "frameInfo", "L" FRAMEINFO_TYPE ";");
		jobject fi = env->GetObjectField(icj->obj, fi_id);

		setFloatVar(env, fi, "max", icj->infi.temp(icj->infi.temp_max));
		setIntVar(env, fi, "max_x", icj->infi.temp_max_x);
		setIntVar(env, fi, "max_y", icj->infi.temp_max_y);
		setFloatVar(env, fi, "min", icj->infi.temp(icj->infi.temp_min));
		setIntVar(env, fi, "min_x", icj->infi.temp_min_x);
		setIntVar(env, fi, "min_y", icj->infi.temp_min_y);
		setFloatVar(env, fi, "center", icj->infi.temp(icj->infi.temp_center));
		setFloatVar(env, fi, "avg", icj->infi.temp(icj->infi.temp_avg));

		setIntVar(env, fi, "width", icj->infi.width);
		setIntVar(env, fi, "height", icj->infi.height);

		setFloatVar(env, fi, "correction", icj->infi.correction);
		setFloatVar(env, fi, "temp_reflected", icj->infi.temp_reflected);
		setFloatVar(env, fi, "temp_air", icj->infi.temp_air);
		setFloatVar(env, fi, "humidity", icj->infi.humidity);
		setFloatVar(env, fi, "emissivity", icj->infi.emissivity);
		setFloatVar(env, fi, "distance", icj->infi.distance);

		/* Make a Java array from the temperature array. */
		int temp_len = icj->infi.width * icj->infi.height;
		jfieldID jtemp_id = env->GetFieldID(cls, "temp", "[F");
		jfloatArray jtemp = (jfloatArray) env->GetObjectField(icj->obj, jtemp_id);
		if (!jtemp || env->GetArrayLength(jtemp) != temp_len) {
			jtemp = env->NewFloatArray(temp_len);
			env->SetObjectField(icj->obj, jtemp_id, jtemp);
		}
		env->SetFloatArrayRegion(jtemp, 0, temp_len, icj->temp);

		/* Call the callback. */
		jmethodID mid = env->GetMethodID(cls, "frameCallback", "(L" FRAMEINFO_TYPE ";[F)V");
		env->CallVoidMethod(icj->obj, mid, fi, jtemp);

		/* Clean up. */
		env->DeleteLocalRef(jtemp);
		env->DeleteLocalRef(fi);
		env->DeleteLocalRef(cls);

		/* Tell the callback's thread we're done. */
		pthread_cond_broadcast(&icj->jthread_cond);
	}
	pthread_mutex_unlock(&icj->jthread_mutex);
	javaVM->DetachCurrentThread();
	return NULL;
}

extern "C" {

JNIEXPORT jlong Java_be_ntmn_libinficam_InfiCam_nativeNew(JNIEnv *env, jclass cls, jobject self) {
	InfiCamJNI *icj = new InfiCamJNI(env, self);
	/* Make sure the mutexes etc are initialized before starting the thread. */
	if (pthread_mutex_init(&icj->jthread_mutex, NULL)) {
		delete icj;
		return 0;
	}
	if (pthread_cond_init(&icj->jthread_cond, NULL)) {
		pthread_mutex_destroy(&icj->jthread_mutex);
		delete icj;
		return 1;
	}
	if (pthread_create(&icj->jthread, NULL, jthread_run, (void *) icj)) {
		pthread_mutex_destroy(&icj->jthread_mutex);
		pthread_cond_destroy(&icj->jthread_cond);
		delete icj;
		return 2;
	}
	return (jlong) icj;
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_nativeDelete(JNIEnv *env, jclass cls, jlong ptr) {
	InfiCamJNI *icj = (InfiCamJNI *) ptr;
	icj->disconnect(); /* Make sure we are disconnected, the callbacks can't come. */
	pthread_mutex_lock(&icj->jthread_mutex);
	icj->jthread_stop = 1;
	pthread_cond_broadcast(&icj->jthread_cond);
	pthread_mutex_unlock(&icj->jthread_mutex);
	pthread_join(icj->jthread, NULL);
	pthread_cond_destroy(&icj->jthread_cond);
	pthread_mutex_destroy(&icj->jthread_mutex);
	ANativeWindow *window = icj->window;
	delete icj; /* Delete also disconnects. */
	if (window != NULL) /* No need to lock, callback isn't called after disconnect. */
		ANativeWindow_release(window);
}

JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_nativeConnect(JNIEnv *env, jobject self, jint fd) {
	InfiCamJNI *icj = getObject(env, self);
	int ret = icj->connect(fd);
	if (ret)
		return ret;
	pthread_mutex_lock(&icj->jthread_mutex);
	if (icj->window != NULL) { /* Connect means the size may have changed. */
		if (ANativeWindow_setBuffersGeometry(
				icj->window, icj->infi.width, icj->infi.height, WINDOW_FORMAT_RGBX_8888)) {
			icj->disconnect();
			pthread_mutex_unlock(&icj->jthread_mutex);
			return 1;
		}
		if (icj->rgb != NULL)
			free(icj->rgb);
		icj->rgb = (uint32_t *) calloc(icj->infi.width * icj->infi.height, sizeof(uint32_t));
		if (icj->rgb == NULL) {
			icj->disconnect();
			pthread_mutex_unlock(&icj->jthread_mutex);
			return 2;
		}
	}
	pthread_mutex_unlock(&icj->jthread_mutex);
	return 0;
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_disconnect(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	icj->disconnect();
}

JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_nativeStartStream(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	if (icj->stream_start(frame_callback, NULL))
		return 1;
	return 0;
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_stopStream(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	icj->stream_stop();
}

JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_nativeSetSurface(JNIEnv *env, jobject self,
																jobject surface) {
	InfiCamJNI *icj = getObject(env, self);
	pthread_mutex_lock(&icj->jthread_mutex);

	/* Remove surface if we had one. */
	if (icj->window != NULL) {
		ANativeWindow_release(icj->window);
		icj->window = NULL;
	}
	if (icj->rgb != NULL) {
		free(icj->rgb);
		icj->rgb = NULL;
	}

	/* Set new surface if we have one. */
	if (surface != NULL) {
		icj->window = ANativeWindow_fromSurface(env, surface);
		if (icj->window == NULL) {
			pthread_mutex_unlock(&icj->jthread_mutex);
			return 1;
		}
		/* Size is set to 0 initially and by disconnect(), we can't resize to 0x0. */
		if (icj->infi.width != 0 && icj->infi.height != 0) {
			if (ANativeWindow_setBuffersGeometry(icj->window, icj->infi.width, icj->infi.height,
												 WINDOW_FORMAT_RGBX_8888)) {
				ANativeWindow_release(icj->window);
				icj->window = NULL;
				pthread_mutex_unlock(&icj->jthread_mutex);
				return 2;
			}
			icj->rgb = (uint32_t *) calloc(icj->infi.width * icj->infi.height, sizeof(uint32_t));
			if (icj->rgb == NULL) {
				ANativeWindow_release(icj->window);
				icj->window = NULL;
				pthread_mutex_unlock(&icj->jthread_mutex);
				return 3;
			}
		}
	}

	pthread_mutex_unlock(&icj->jthread_mutex);
	return 0;
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setRange(JNIEnv *env, jobject self,
														jint range) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_range(range);
}

JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_getWidth(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	return icj->infi.width;
}

JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_getHeight(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	return icj->infi.height;
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setDistanceMultiplier(JNIEnv *env, jobject self,
																	 jfloat dm) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_distance_multiplier(dm);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setCorrection(JNIEnv *env, jobject self,
															 jfloat val) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_correction(val);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setTempReflected(JNIEnv *env, jobject self,
																jfloat val) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_temp_reflected(val);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setTempAir(JNIEnv *env, jobject self, jfloat val) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_temp_air(val);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setHumidity(JNIEnv *env, jobject self, jfloat val) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_humidity(val);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setEmissivity(JNIEnv *env, jobject self,
															 jfloat val) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_emissivity(val);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setDistance(JNIEnv *env, jobject self, jfloat val) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_distance(val);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setParams(JNIEnv *env, jobject self, jfloat corr,
													  jfloat t_ref, jfloat t_air, jfloat humi,
													  jfloat emi, jfloat dist) {
	InfiCamJNI *icj = getObject(env, self);
	icj->set_params(corr, t_ref, t_air, humi, emi, dist);
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_storeParams(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	icj->store_params();
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_closeShutter(JNIEnv *env, jobject self) {
    InfiCam *cam = getObject(env, self);
    if (cam != NULL) {
        cam->close_shutter();
    }
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setRawSensor(JNIEnv *env, jobject self, jboolean raw) {
    InfiCam *cam = getObject(env, self);
    if (cam != NULL) {
        cam->set_raw_sensor(raw);
        cam->infi.raw_sensor = raw;
    }
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setP2Pro(JNIEnv *env, jobject self, jboolean p2_pro) {
    InfiCam *cam = getObject(env, self);
    if (cam != NULL) {
        cam->set_p2_pro(p2_pro);
        cam->infi.p2_pro = p2_pro;
    }
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_updateTable(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	icj->update_table();
}

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_calibrate(JNIEnv *env, jobject self) {
	InfiCamJNI *icj = getObject(env, self);
	icj->calibrate();
}

JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_nativeSetPalette(JNIEnv *env, jobject self,
																jintArray palette) {
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

JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_applyPalette(JNIEnv *env, jobject self, jfloat min,
		jfloat max) {
	InfiCamJNI *icj = getObject(env, self);
	/* Update the surface if we have one. */
	if (icj->window != NULL) {
		ANativeWindow_Buffer buffer;

		icj->infi.palette_appy(icj->temp, icj->rgb,
							 isnan(min) ? icj->infi.temp(icj->infi.temp_min) : min,
							 isnan(max) ? icj->infi.temp(icj->infi.temp_max) : max);

		if (ANativeWindow_lock(icj->window, &buffer, NULL) == 0) {
			const uint8_t *src = (uint8_t *) icj->rgb;
			const int src_w = icj->infi.width * 4;
			const int dst_w = buffer.width * 4;
			const int dst_step = buffer.stride * 4;

			/* Set w and h to be the smallest of the two rectangles. */
			const int w = src_w < dst_w ? src_w : dst_w;
			const int h = icj->infi.height < buffer.height ? icj->infi.height : buffer.height;

			/* Transfer from frame data to the Surface */
			uint8_t *dst = (uint8_t *) buffer.bits;
			for (int i = 0; i < h; ++i) {
				memcpy(dst, src, w);
				dst += dst_step;
				src += src_w;
			}

			ANativeWindow_unlockAndPost(icj->window);
		}
	}
}

} /* extern "C" */

