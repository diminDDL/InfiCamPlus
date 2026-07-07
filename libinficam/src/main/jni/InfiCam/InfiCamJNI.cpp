#include "CameraSettings.h"
#include "InfiCam.h"

#include <atomic>
#include <cstdint>
#include <jni.h>
#include <android/native_window_jni.h>
#include <pthread.h>
#include "Utils.h"

#define FRAMEINFO_TYPE "be/ntmn/libinficam/InfiCam$FrameInfo"
#define CAMSETTINGS_TYPE "be/ntmn/libinficam/InfiCam$CamSettings"

JavaVM *javaVM = nullptr;

extern "C" JNICALL jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	javaVM = vm;
	JNIEnv *env;
	if (vm->GetEnv((void **) &env, JNI_VERSION_1_6))
		return JNI_ERR;
	return JNI_VERSION_1_6;
}

class InfiCamJNI{
public:

	InfiCam cam{this};
	JNIEnv *env;
	jobject obj;
	std::atomic_bool jthreads_stop = false;

	int width = 0;
	int height = 0;

	/* Initialized elsewhere to avoid needing exceptions. */
	pthread_t jthread_frame{};
	pthread_mutex_t jthread_frame_mutex{};
	pthread_cond_t jthread_frame_cond{};
	const float * jthread_frame_temp = nullptr; //for transfer between threads
	CameraSettings jthread_frame_cam_settings; //for transfer between threads

	pthread_t jthread_settings{};
	pthread_mutex_t jthread_settings_mutex{};
	pthread_cond_t jthread_settings_cond{};
	CameraSettings jthread_settings_cam_settings; //for transfer between threads

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

/* Set an short variable in the a Java class. */
static void setShortVar(JNIEnv *env, jobject obj, const char *name, jshort value) {
	jclass cls = env->GetObjectClass(obj);
	jfieldID nativeObjectPointerID = env->GetFieldID(cls, name, "S");
	env->SetShortField(obj, nativeObjectPointerID, value);
	env->DeleteLocalRef(cls);
}

/* Set an float variable in the a Java class. */
static void setFloatVar(JNIEnv *env, jobject obj, const char *name, jfloat value) {
	jclass cls = env->GetObjectClass(obj);
	jfieldID nativeObjectPointerID = env->GetFieldID(cls, name, "F");
	env->SetFloatField(obj, nativeObjectPointerID, value);
	env->DeleteLocalRef(cls);
}

/* Frame callback that notifies jthread (described later). */
static void settings_callback(const void* p_InfiCamJNI, const CameraSettings& p_cam_settings){
	auto* inficam_jni = (InfiCamJNI*) p_InfiCamJNI;
	pthread_mutex_lock(&inficam_jni->jthread_settings_mutex);

	inficam_jni->jthread_settings_cam_settings = p_cam_settings;

	pthread_cond_broadcast(&inficam_jni->jthread_settings_cond);
	/* Now we wait for the other thread to finish and signal the same condition, this works
	 *   because only threads that are currently waiting get signaled.
	 */
	while (pthread_cond_wait(&inficam_jni->jthread_settings_cond, &inficam_jni->jthread_settings_mutex));
	pthread_mutex_unlock(&inficam_jni->jthread_settings_mutex);
}

/* Frame callback that notifies jthread (described later). */
static void frame_callback(const void* p_InfiCamJNI, const float *p_temp, const CameraSettings& p_cam_settings) {
	auto* inficam_jni = (InfiCamJNI*) p_InfiCamJNI;
	pthread_mutex_lock(&inficam_jni->jthread_frame_mutex);

	inficam_jni->jthread_frame_temp = p_temp;
	inficam_jni->jthread_frame_cam_settings = p_cam_settings;

	pthread_cond_broadcast(&inficam_jni->jthread_frame_cond);
	/* Now we wait for the other thread to finish and signal the same condition, this works
	 *   because only threads that are currently waiting get signaled.
	 */
	while (pthread_cond_wait(&inficam_jni->jthread_frame_cond, &inficam_jni->jthread_frame_mutex));
	pthread_mutex_unlock(&inficam_jni->jthread_frame_mutex);
}

/* This thread will attach to the JVM and draw frames to an Android surface, then call a Java
 *   callback, the reason we need this is because the callback from libuvc doesn't allow us to do
 *   something at the end of the thread that calls.
 *   This will be called by the UVC callback.
 *   We need to detach the thread from the JVM when we are done.
 */
static void *jthread_frame_run(void *a) {
	auto *t = (InfiCamJNI *) a;
	JNIEnv *env;
	javaVM->AttachCurrentThread(&env, nullptr);
	pthread_mutex_lock(&t->jthread_frame_mutex);
	while (true) {
		while (!t->jthreads_stop && pthread_cond_wait(&t->jthread_frame_cond, &t->jthread_frame_mutex));
		if (t->jthreads_stop) {
			pthread_mutex_unlock(&t->jthread_frame_mutex);
			break;
		}

		/* Fill the FrameInfo struct and the CamSettings struct within it. */
		jclass cls = env->GetObjectClass(t->obj);
		jfieldID fi_id = env->GetFieldID(cls, "framcb_frameInfo", "L" FRAMEINFO_TYPE ";");
		jobject fi = env->GetObjectField(t->obj, fi_id);
		jclass fiCls = env->GetObjectClass(fi);
		jfieldID cm_id = env->GetFieldID(fiCls, "settings", "L" CAMSETTINGS_TYPE ";");
		if(cm_id== nullptr)__android_log_assert(nullptr, LOG_TAG,"cm_id is null"); // *kaboom*
		jobject cm = env->GetObjectField(fi, cm_id);
		if(cm== nullptr)__android_log_assert(nullptr, LOG_TAG,"cm is null"); // *kaboom*

		setIntVar(env, fi, "width", t->width);
		setIntVar(env, fi, "height", t->height);
		setIntVar(env, cm, "range", (int)t->jthread_frame_cam_settings.temperature_range);
		setFloatVar(env, cm, "max_temp_clipping", t->jthread_settings_cam_settings.max_temperature_clipping);
		setFloatVar(env, cm, "correction", t->jthread_frame_cam_settings.temperature_correction);
		setFloatVar(env, cm, "temp_reflected", t->jthread_frame_cam_settings.reflection_temperature);
		setFloatVar(env, cm, "temp_air", t->jthread_frame_cam_settings.air_temperature);
		setFloatVar(env, cm, "humidity", t->jthread_frame_cam_settings.humidity);
		setFloatVar(env, cm, "emissivity", t->jthread_frame_cam_settings.emissivity);
		setShortVar(env, cm, "distance", t->jthread_frame_cam_settings.distance);

		/* Make a Java array from the temperature array. */
		int temp_len = t->height * t->width;
		jfieldID jtemp_id = env->GetFieldID(cls, "framcb_temp", "[F");
		auto jtemp = (jfloatArray) env->GetObjectField(t->obj, jtemp_id);
		if (!jtemp || env->GetArrayLength(jtemp) != temp_len) {
			jtemp = env->NewFloatArray(temp_len);
			env->SetObjectField(t->obj, jtemp_id, jtemp);
		}
		env->SetFloatArrayRegion(jtemp, 0, temp_len, t->jthread_frame_temp);

		/* Call the callback. */
		jmethodID mid = env->GetMethodID(cls, "frameCallback", "(L" FRAMEINFO_TYPE ";[F)V");
		env->CallVoidMethod(t->obj, mid, fi, jtemp);

		/* Clean up. */
		env->DeleteLocalRef(jtemp);
		env->DeleteLocalRef(cm);
		env->DeleteLocalRef(fi);
		env->DeleteLocalRef(cls);

		/* Tell the callback's thread we're done. */
		pthread_cond_broadcast(&t->jthread_frame_cond);
	}
	pthread_mutex_unlock(&t->jthread_frame_mutex);
	javaVM->DetachCurrentThread();
	return nullptr;
}

/* This thread will attach to the JVM and draw frames to an Android surface, then call a Java
 *   callback, the reason we need this is because the callback from libuvc doesn't allow us to do
 *   something at the end of the thread that calls.
 *   This may or may not be called by the UVC callback.
 *   We need to detach the thread from the JVM when we are done.
 */
static void *jthread_settings_run(void *a) {
	auto *t = (InfiCamJNI *) a;
	JNIEnv *env;
	javaVM->AttachCurrentThread(&env, nullptr);
	pthread_mutex_lock(&t->jthread_settings_mutex);
	while (true) {
		while (!t->jthreads_stop && pthread_cond_wait(&t->jthread_settings_cond, &t->jthread_settings_mutex));
		if (t->jthreads_stop) {
			pthread_mutex_unlock(&t->jthread_settings_mutex);
			break;
		}
		/* Fill the CamSettings struct. */
		jclass cls = env->GetObjectClass(t->obj);
		jfieldID cm_id = env->GetFieldID(cls, "setcb_camSettings", "L" CAMSETTINGS_TYPE ";");
		jobject cm = env->GetObjectField(t->obj, cm_id);


		setIntVar(env, cm, "range", (int)t->jthread_settings_cam_settings.temperature_range);
		setFloatVar(env, cm, "max_temp_clipping", t->jthread_settings_cam_settings.max_temperature_clipping);
		setFloatVar(env, cm, "correction", t->jthread_settings_cam_settings.temperature_correction);
		setFloatVar(env, cm, "temp_reflected", t->jthread_settings_cam_settings.reflection_temperature);
		setFloatVar(env, cm, "temp_air", t->jthread_settings_cam_settings.air_temperature);
		setFloatVar(env, cm, "humidity", t->jthread_settings_cam_settings.humidity);
		setFloatVar(env, cm, "emissivity", t->jthread_settings_cam_settings.emissivity);
		setShortVar(env, cm, "distance", t->jthread_settings_cam_settings.distance);

		LOGD("Calling the app's settingsCallback.\n");

		/* Call the callback. */
		jmethodID mid = env->GetMethodID(cls, "settingsCallback", "(L" CAMSETTINGS_TYPE ";)V");
		env->CallVoidMethod(t->obj, mid, cm);

		env->DeleteLocalRef(cm);
		env->DeleteLocalRef(cls);

		/* Tell the callback's thread we're done. */
		pthread_cond_broadcast(&t->jthread_settings_cond);
	}
	pthread_mutex_unlock(&t->jthread_settings_mutex);
	javaVM->DetachCurrentThread();
	return nullptr;
}


extern "C" {

	JNIEXPORT jlong Java_be_ntmn_libinficam_InfiCam_nativeNew(JNIEnv *env, jclass cls, jobject self) {
		auto *t = new InfiCamJNI(env, self);
		/* Make sure the mutexes etc are initialized before starting the thread. */
		if (pthread_mutex_init(&t->jthread_frame_mutex, nullptr)) {
			delete t;
			return 0;
		}
		if (pthread_cond_init(&t->jthread_frame_cond, nullptr)) {
			pthread_mutex_destroy(&t->jthread_frame_mutex);
			delete t;
			return 1;
		}
		if (pthread_create(&t->jthread_frame, nullptr, jthread_frame_run, (void *) t)) {
			pthread_mutex_destroy(&t->jthread_frame_mutex);
			pthread_cond_destroy(&t->jthread_frame_cond);
			delete t;
			return 2;
		}
		if (pthread_mutex_init(&t->jthread_settings_mutex, nullptr)) {
			delete t;
			return 3;
		}
		if (pthread_cond_init(&t->jthread_settings_cond, nullptr)) {
			pthread_mutex_destroy(&t->jthread_settings_mutex);
			delete t;
			return 4;
		}
		if (pthread_create(&t->jthread_settings, nullptr, jthread_settings_run, (void *) t)) {
			pthread_mutex_destroy(&t->jthread_settings_mutex);
			pthread_cond_destroy(&t->jthread_settings_cond);
			delete t;
			return 5;
		}
		return (jlong) t;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_nativeDelete(JNIEnv *env, jclass cls, jlong ptr) {
		auto *t = (InfiCamJNI *) ptr;
		t->cam.disconnect(); /* Make sure we are disconnected, the callbacks can't come. */
		t->jthreads_stop = true;
		pthread_mutex_lock(&t->jthread_frame_mutex);
		pthread_cond_broadcast(&t->jthread_settings_cond);
		pthread_mutex_unlock(&t->jthread_frame_mutex);
		pthread_mutex_lock(&t->jthread_settings_mutex);
		pthread_cond_broadcast(&t->jthread_frame_cond);
		pthread_mutex_unlock(&t->jthread_settings_mutex);
		pthread_join(t->jthread_frame, nullptr);
		pthread_join(t->jthread_settings, nullptr);
		pthread_cond_destroy(&t->jthread_frame_cond);
		pthread_mutex_destroy(&t->jthread_frame_mutex);
		pthread_cond_destroy(&t->jthread_settings_cond);
		pthread_mutex_destroy(&t->jthread_settings_mutex);
		delete t; /* Delete also disconnects. */
	}

	JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_nativeConnect(JNIEnv *env, jobject self, jint fd) {
		InfiCamJNI *t = getObject(env, self);
		int ret = t->cam.connect(fd, t->width, t->height, settings_callback);
		if (ret)
			return ret;
		return 0;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_disconnect(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.disconnect();
	}

	JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_nativeStartStream(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		if (t->cam.stream_start(frame_callback))
			return 1;
		return 0;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_stopStream(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.stream_stop();
	}

	/*
	 * Range must be the id of the corresponding range queried from "Java_be_ntmn_libinficam_InfiCam_getRanges"
	 * For example, 0 means the range -20 to 120.
	 */
	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setRange(JNIEnv *env, jobject self,
															jint range) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_range(range);
	}

	JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_getWidth(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->width;
	}

	/* Returns the REAL height of the thermographic frame. The metadata is an implementation detail to the rest of the app.*/
	JNIEXPORT jint Java_be_ntmn_libinficam_InfiCam_getHeight(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->height;
	}

	/* Returns a 2D array containing the supported ranges, in the format:
	 *	  RANGE 0	 RANGE 1	RANGE 2
	 *	{{min,max},{min,max}},{{min,max}}
	 */
	JNIEXPORT jobjectArray Java_be_ntmn_libinficam_InfiCam_getRanges(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);

		jclass floatArrayClass = env->FindClass("[F");
	   if (floatArrayClass == nullptr) {
		   return nullptr;
	   }

	   jobjectArray result = env->NewObjectArray(2, floatArrayClass, nullptr);
	   if (result == nullptr) {
		   return nullptr;
	   }
	   auto ranges = t->cam.get_ranges();
	   for (jsize i = 0; i < 2; ++i) {
		   jfloatArray row = env->NewFloatArray(2);
		   if (row == nullptr) {
			   return nullptr;
		   }

		   env->SetFloatArrayRegion(row, 0, 2, ranges[i].data());

		   // Store the row in the outer array.
		   env->SetObjectArrayElement(result, i, row);

		   // Delete the local ref to avoid exhausting the local reference table
		   // when rows is large.
		   env->DeleteLocalRef(row);
	   }

	   return result;
	}

	/*
	 * We read and write the settings through the InfiCam object.
	 *
	 * Settings may not represent the current camera state for non-raw sensors !
	 * That is because the camera holds the data and only sends it inside video frames.
	 */

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setCorrection(JNIEnv *env, jobject self,
																 jfloat val) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_correction(val);
	}
	JNIEXPORT float Java_be_ntmn_libinficam_InfiCam_getCorrection(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->cam.cam_settings.temperature_correction;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setTempReflected(JNIEnv *env, jobject self, float val) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_temp_reflected(val);
	}
	JNIEXPORT float Java_be_ntmn_libinficam_InfiCam_getTempReflected(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->cam.cam_settings.reflection_temperature;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setTempAir(JNIEnv *env, jobject self, jfloat val) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_temp_air(val);
	}
	JNIEXPORT float Java_be_ntmn_libinficam_InfiCam_getTempAir(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->cam.cam_settings.air_temperature;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setHumidity(JNIEnv *env, jobject self, jfloat val) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_humidity(val);
	}
	JNIEXPORT float Java_be_ntmn_libinficam_InfiCam_getHumidity(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->cam.cam_settings.humidity;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setEmissivity(JNIEnv *env, jobject self, jfloat val) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_emissivity(val);
	}
	JNIEXPORT float Java_be_ntmn_libinficam_InfiCam_getEmissivity(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->cam.cam_settings.emissivity;
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setDistance(JNIEnv *env, jobject self, jshort val) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_distance(val);
	}
	JNIEXPORT short Java_be_ntmn_libinficam_InfiCam_getDistance(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return (int16_t)t->cam.cam_settings.distance; //Short instead of unsigned short because java doesn't do unsigned. Shouldn't matter.
	}

	//No-op on raw sensors
	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_storeParams(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.store_params();
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_lockShutter(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.lock_shutter();
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_unlockShutter(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.unlock_shutter();
	}
	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_calibrate(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.calibrate();
	}
	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_calibrateBlocking(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		pthread_mutex_lock(&t->cam.cal_mutex);
		t->cam.calibrate();
		while(t->cam.isCalibrating()){
			pthread_cond_wait(&t->cam.cal_request, &t->cam.cal_mutex);
		}
		pthread_mutex_unlock(&t->cam.cal_mutex);
	}

	JNIEXPORT jboolean Java_be_ntmn_libinficam_InfiCam_isCalibrating(JNIEnv *env, jobject self) {
		InfiCamJNI *t = getObject(env, self);
		return t->cam.isCalibrating();
	}

	JNIEXPORT jboolean Java_be_ntmn_libinficam_InfiCam_setCalibrationSuppressed(JNIEnv *env, jobject self, jboolean suppress) {
		InfiCamJNI *t = getObject(env, self);
		return t->cam.setCalibrationSuppressed(suppress);
	}

	JNIEXPORT void Java_be_ntmn_libinficam_InfiCam_setAutoShutterSettings(JNIEnv *env, jobject self, jboolean enable, jint interval_min, jint interval_max) {
		InfiCamJNI *t = getObject(env, self);
		t->cam.set_auto_shutter_settings(enable, interval_min, interval_max);
	}

} /* extern "C" */
