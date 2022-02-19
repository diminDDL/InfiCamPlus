/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: serenegiant_usb_UVCCamera.cpp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

#if 1	// デバッグ情報を出さない時
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// LOGV/LOGD/MARKを出力しない時
		#endif
	#undef USE_LOGALL			// 指定したLOGxだけを出力
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
	#undef NDEBUG
#endif

#include <jni.h>
#include <android/native_window_jni.h>

#include "libUVCCamera.h"
#include "UVCCamera.h"

/**
 * set the value into the long field
 * @param env: this param should not be null
 * @param bullet_obj: this param should not be null
 * @param field_name
 * @params val
 */
static jlong setField_long(JNIEnv *env, jobject java_obj, const char *field_name, jlong val) {
#if LOCAL_DEBUG
	LOGV("setField_long:");
#endif

	jclass clazz = env->GetObjectClass(java_obj);
	jfieldID field = env->GetFieldID(clazz, field_name, "J");
	if (LIKELY(field))
		env->SetLongField(java_obj, field, val);
	else {
		LOGE("__setField_long:field '%s' not found", field_name);
	}
#ifdef ANDROID_NDK
	env->DeleteLocalRef(clazz);
#endif
	return val;
}

/**
 * @param env: this param should not be null
 * @param bullet_obj: this param should not be null
 */
static jlong __setField_long(JNIEnv *env, jobject java_obj, jclass clazz, const char *field_name,
                             jlong val) {
#if LOCAL_DEBUG
	LOGV("__setField_long:");
#endif

	jfieldID field = env->GetFieldID(clazz, field_name, "J");
	if (LIKELY(field))
		env->SetLongField(java_obj, field, val);
	else {
		LOGE("__setField_long:field '%s' not found", field_name);
	}
	return val;
}

/**
 * @param env: this param should not be null
 * @param bullet_obj: this param should not be null
 */
jint __setField_int(JNIEnv *env, jobject java_obj, jclass clazz, const char *field_name, jint val) {
	LOGV("__setField_int:");

	jfieldID id = env->GetFieldID(clazz, field_name, "I");
	if (LIKELY(id))
		env->SetIntField(java_obj, id, val);
	else {
		LOGE("__setField_int:field '%s' not found", field_name);
		env->ExceptionClear();	// clear java.lang.NoSuchFieldError exception
	}
	return val;
}

/**
 * set the value into int field
 * @param env: this param should not be null
 * @param java_obj: this param should not be null
 * @param field_name
 * @params val
 */
jint setField_int(JNIEnv *env, jobject java_obj, const char *field_name, jint val) {
	LOGV("setField_int:");

	jclass clazz = env->GetObjectClass(java_obj);
	__setField_int(env, java_obj, clazz, field_name, val);
#ifdef ANDROID_NDK
	env->DeleteLocalRef(clazz);
#endif
	return val;
}

static ID_TYPE nativeCreate(JNIEnv *env, jobject thiz) {
	ENTER();
	UVCCamera *camera = new UVCCamera();
	setField_long(env, thiz, "mNativePtr", reinterpret_cast<ID_TYPE>(camera));
	RETURN(reinterpret_cast<ID_TYPE>(camera), ID_TYPE);
}

// native側のカメラオブジェクトを破棄
static void nativeDestroy(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	ENTER();
	setField_long(env, thiz, "mNativePtr", 0);
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		SAFE_DELETE(camera);
	}
	EXIT();
}

//======================================================================
// カメラへ接続
static jint nativeConnect(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint vid, jint pid,
                          jint fd, jint busNum, jint devAddr, jstring usbfs_str) {
	ENTER();
	int result = JNI_ERR;
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	const char *c_usbfs = env->GetStringUTFChars(usbfs_str, JNI_FALSE);
	if (LIKELY(camera && (fd > 0))) {
//		libusb_set_debug(NULL, LIBUSB_LOG_LEVEL_DEBUG);
		result =  camera->connect(vid, pid, fd, busNum, devAddr, c_usbfs);
	}
	env->ReleaseStringUTFChars(usbfs_str, c_usbfs);
	RETURN(result, jint);
}

// カメラとの接続を解除
static jint nativeRelease(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	ENTER();
	int result = JNI_ERR;
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->release();
	}
	RETURN(result, jint);
}

//======================================================================
static jint nativeSetStatusCallback(JNIEnv *env, jobject thiz, ID_TYPE id_camera,
                                    jobject jIStatusCallback) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		jobject status_callback_obj = env->NewGlobalRef(jIStatusCallback);
		result = camera->setStatusCallback(env, status_callback_obj);
	}
	RETURN(result, jint);
}

static jobject nativeGetSupportedSize(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	ENTER();
	jstring result = NULL;
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		char *c_str = camera->getSupportedSize();
		if (LIKELY(c_str)) {
			result = env->NewStringUTF(c_str);
			free(c_str);
		}
	}
	RETURN(result, jobject);
}

static jbyteArray nativeGetByteArrayTemperaturePara(JNIEnv *env, jobject thiz, ID_TYPE id_camera,
                                                    int len) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	jbyteArray array=(env)->NewByteArray(len);
	uint8_t *para=(uint8_t *)malloc(len*sizeof(uint8_t));
	int status=0;
	if (LIKELY(camera)) {
        status=camera->getByteArrayTemperaturePara((uint8_t*)para);
        LOGE("nativeGetByteArrayTemperaturePara:%d,%d,%d,%d,%d",para[1],para[5],para[9],para[13],para[17]);
	}
	env->SetByteArrayRegion(array,0,len*sizeof(jbyte),(jbyte*)para);
	free(para);
	return array;
}

static jint nativeSetUserPalette(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint typeOfPalette,
                                 jbyteArray palette) {
	ENTER();
	int status=0;
        jbyte *  arr =env-> GetByteArrayElements(palette,0);
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
        camera->SetUserPalette((uint8_t*)arr,typeOfPalette);
	}
	env->ReleaseByteArrayElements(palette,arr,0);
	RETURN(status,jint);
}

//======================================================================
// プレビュー画面の大きさをセット
static jint nativeSetPreviewSize(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint width,
                                 jint height, jint min_fps, jint max_fps, jint mode,
                                 jfloat bandwidth) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		return camera->setPreviewSize(width, height, min_fps, max_fps, mode, bandwidth);
	}
	RETURN(JNI_ERR, jint);
}

static jint nativeStartPreview(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		return camera->startPreview();
	}
	RETURN(JNI_ERR, jint);
}

// プレビューを停止
static jint nativeStopPreview(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->stopPreview();
	}
	RETURN(result, jint);
}

static jint nativeSetPreviewDisplay(JNIEnv *env, jobject thiz, ID_TYPE id_camera,
                                    jobject jSurface) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		ANativeWindow *preview_window = jSurface ? ANativeWindow_fromSurface(env, jSurface) : NULL;
		result = camera->setPreviewDisplay(preview_window);
	}
	RETURN(result, jint);
}

static jint nativeSetTemperatureCallback(JNIEnv *env, jobject thiz, ID_TYPE id_camera,
                                         jobject jITemperatureCallback) {
//LOGE("nativeSetTemperatureCallback1");
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	LOGE("nativeSetTemperatureCallback2");
		jobject temperature_callback_obj = env->NewGlobalRef(jITemperatureCallback);
		result = camera->setTemperatureCallback(env, temperature_callback_obj);
	}
	RETURN(result, jint);
}

static void nativeWhenShutRefresh(JNIEnv *env, jobject thiz,ID_TYPE id_camera) {
//LOGE("nativeWhenShutRefresh");
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		camera->whenShutRefresh();
	}
	EXIT();
}

static void nativeWhenChangeTempPara(JNIEnv *env, jobject thiz,ID_TYPE id_camera) {
//LOGE("nativeWhenShutRefresh");
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		camera->whenChangeTempPara();
	}
	EXIT();
}

static jint nativeStartStopTemp(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint startStop) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		if(startStop) {//1 start 0stop
			result = camera->startTemp();
		} else {
			result = camera->stopTemp();
		}
	}
	RETURN(result, jint);
}

static void nativeChangePalette(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint typeOfPalette) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->changePalette(typeOfPalette);
	}
	EXIT();
}

static void nativeSetTempRange(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint range) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->setTempRange(range);
	}
	EXIT();
}

static void nativeSetShutterFix(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jfloat mShutterFix) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->setShutterFix(mShutterFix);
	}
	EXIT();
}

static void nativeSetCameraLens(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint mCameraLens) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->setCameraLens(mCameraLens);
	}
	EXIT();
}

//======================================================================
// Java mnethod correspond to this function should not be a static mathod
static jint nativeUpdateBrightnessLimit(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		int min, max, def;
		result = camera->updateBrightnessLimit(min, max, def);
		if (!result) {
			// Java側へ書き込む
			setField_int(env, thiz, "mBrightnessMin", min);
			setField_int(env, thiz, "mBrightnessMax", max);
			setField_int(env, thiz, "mBrightnessDef", def);
		}
	}
	RETURN(result, jint);
}

static jint nativeSetBrightness(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint brightness) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->setBrightness(brightness);
	}
	RETURN(result, jint);
}

static jint nativeGetBrightness(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	jint result = 0;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->getBrightness();
	}
	RETURN(result, jint);
}

//======================================================================
// Java mnethod correspond to this function should not be a static mathod
static jint nativeUpdateContrastLimit(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		int min, max, def;
		result = camera->updateContrastLimit(min, max, def);
		if (!result) {
			// Java側へ書き込む
			setField_int(env, thiz, "mContrastMin", min);
			setField_int(env, thiz, "mContrastMax", max);
			setField_int(env, thiz, "mContrastDef", def);
		}
	}
	RETURN(result, jint);
}

static jint nativeSetContrast(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint contrast) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->setContrast(contrast);
	}
	RETURN(result, jint);
}

static jint nativeGetContrast(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
	jint result = 0;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->getContrast();
	}
	RETURN(result, jint);
}

static jint nativeSetZoom(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint zoom) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->setZoom(zoom);
	}
	RETURN(result, jint);
}

//**********************************************************************
//
//**********************************************************************
jint registerNativeMethods(JNIEnv* env, const char *class_name, JNINativeMethod *methods,
                           int num_methods) {
	int result = 0;

	jclass clazz = env->FindClass(class_name);
	if (LIKELY(clazz)) {
		int result = env->RegisterNatives(clazz, methods, num_methods);
		if (UNLIKELY(result < 0)) {
			LOGE("registerNativeMethods failed(class=%s)", class_name);
		}
	} else {
		LOGE("registerNativeMethods: class'%s' not found", class_name);
	}
	return result;
}

static JNINativeMethod methods[] = {
	{ "nativeCreate",                       "()J", (void *) nativeCreate },
	{ "nativeDestroy",                      "(J)V", (void *) nativeDestroy },

	{ "nativeConnect",                      "(JIIIIILjava/lang/String;)I", (void *) nativeConnect },
	{ "nativeRelease",                      "(J)I", (void *) nativeRelease },

	{ "nativeSetTemperatureCallback",       "(JLcom/serenegiant/usb/ITemperatureCallback;)I", (void *) nativeSetTemperatureCallback },
    { "nativeWhenShutRefresh",              "(J)V", (void *) nativeWhenShutRefresh },
    { "nativeWhenChangeTempPara",           "(J)V", (void *) nativeWhenChangeTempPara },

	{ "nativeSetStatusCallback",            "(JLcom/serenegiant/usb/IStatusCallback;)I", (void *) nativeSetStatusCallback },

	{ "nativeGetSupportedSize",             "(J)Ljava/lang/String;", (void *) nativeGetSupportedSize },
	{ "nativeSetPreviewSize",               "(JIIIIIF)I", (void *) nativeSetPreviewSize },
	{ "nativeStartPreview",                 "(J)I", (void *) nativeStartPreview },
	{ "nativeStopPreview",                  "(J)I", (void *) nativeStopPreview },
	{ "nativeSetPreviewDisplay",            "(JLandroid/view/Surface;)I", (void *) nativeSetPreviewDisplay },
    { "nativeSetUserPalette",               "(JI[B)I", (void *) nativeSetUserPalette },
	{ "nativeGetByteArrayTemperaturePara",  "(JI)[B", (void *) nativeGetByteArrayTemperaturePara },

    { "nativeStartStopTemp",                "(JI)I", (void *) nativeStartStopTemp },

    { "nativeChangePalette",                "(JI)V", (void *) nativeChangePalette },
    { "nativeSetTempRange",                 "(JI)V", (void *) nativeSetTempRange },
    { "nativeSetShutterFix",                "(JF)V", (void *) nativeSetShutterFix },
    { "nativeSetCameraLens",                "(JI)V", (void *) nativeSetCameraLens },

	{ "nativeUpdateBrightnessLimit",        "(J)I", (void *) nativeUpdateBrightnessLimit },
	{ "nativeSetBrightness",                "(JI)I", (void *) nativeSetBrightness },
	{ "nativeGetBrightness",                "(J)I", (void *) nativeGetBrightness },

	{ "nativeUpdateContrastLimit",          "(J)I", (void *) nativeUpdateContrastLimit },
	{ "nativeSetContrast",                  "(JI)I", (void *) nativeSetContrast },
	{ "nativeGetContrast",                  "(J)I", (void *) nativeGetContrast },

	{ "nativeSetZoom",                      "(JI)I", (void *) nativeSetZoom },
};

int register_uvccamera(JNIEnv *env) {
	LOGV("register_uvccamera:");
	if (registerNativeMethods(env,
		"com/serenegiant/usb/UVCCamera",
		methods, NUM_ARRAY_ELEMENTS(methods)) < 0) {
		return -1;
	}
	return 0;
}
