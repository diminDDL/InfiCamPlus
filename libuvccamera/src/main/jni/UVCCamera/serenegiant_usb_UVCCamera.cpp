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
#include "libusb.h"
#include "libuvc.h"
#include "utilbase.h"
#include "UVCCamera.h"

extern "C" {

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

JNIEXPORT ID_TYPE Java_com_serenegiant_UVCCamera_nativeCreate(JNIEnv *env, jobject thiz) {
	ENTER();
	UVCCamera *camera = new UVCCamera();
	setField_long(env, thiz, "mNativePtr", reinterpret_cast<ID_TYPE>(camera));
	RETURN(reinterpret_cast<ID_TYPE>(camera), ID_TYPE);
}

// native側のカメラオブジェクトを破棄
JNIEXPORT void Java_com_serenegiant_UVCCamera_nativeDestroy(JNIEnv *env, jobject thiz, ID_TYPE id_camera) {
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
JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeConnect(JNIEnv *env, jobject thiz, ID_TYPE id_camera, jint vid, jint pid,
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
JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeRelease(JNIEnv *env, jclass thiz, ID_TYPE id_camera) {
	ENTER();
	int result = JNI_ERR;
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->release();
	}
	RETURN(result, jint);
}

JNIEXPORT jstring Java_com_serenegiant_UVCCamera_nativeGetSupportedSize(JNIEnv *env, jclass thiz, ID_TYPE id_camera) {
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
	RETURN(result, jstring);
}

JNIEXPORT jbyteArray Java_com_serenegiant_UVCCamera_nativeGetByteArrayTemperaturePara(JNIEnv *env, jclass thiz, ID_TYPE id_camera,
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

JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeSetUserPalette(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jint typeOfPalette,
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
JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeSetPreviewSize(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jint width,
                                 jint height, jint min_fps, jint max_fps, jint mode,
                                 jfloat bandwidth) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		return camera->setPreviewSize(width, height, min_fps, max_fps, mode, bandwidth);
	}
	RETURN(JNI_ERR, jint);
}

JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeStartPreview(JNIEnv *env, jclass thiz, ID_TYPE id_camera) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		return camera->startPreview();
	}
	RETURN(JNI_ERR, jint);
}

// プレビューを停止
JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeStopPreview(JNIEnv *env, jclass thiz, ID_TYPE id_camera) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->stopPreview();
	}
	RETURN(result, jint);
}

JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeSetPreviewDisplay(JNIEnv *env, jclass thiz, ID_TYPE id_camera,
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

JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeSetTemperatureCallback(JNIEnv *env, jclass thiz, ID_TYPE id_camera,
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

JNIEXPORT void Java_com_serenegiant_UVCCamera_nativeWhenShutRefresh(JNIEnv *env, jclass thiz,ID_TYPE id_camera) {
//LOGE("nativeWhenShutRefresh");
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		camera->whenShutRefresh();
	}
	EXIT();
}

JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeStartStopTemp(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jint startStop) {
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

JNIEXPORT void Java_com_serenegiant_UVCCamera_nativeChangePalette(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jint typeOfPalette) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->changePalette(typeOfPalette);
	}
	EXIT();
}

JNIEXPORT void Java_com_serenegiant_UVCCamera_nativeSetTempRange(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jint range) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->setTempRange(range);
	}
	EXIT();
}

JNIEXPORT void Java_com_serenegiant_UVCCamera_nativeSetShutterFix(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jfloat mShutterFix) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->setShutterFix(mShutterFix);
	}
	EXIT();
}

JNIEXPORT void Java_com_serenegiant_UVCCamera_nativeSetCameraLens(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jint mCameraLens) {
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
	 camera->setCameraLens(mCameraLens);
	}
	EXIT();
}

JNIEXPORT jint Java_com_serenegiant_UVCCamera_nativeSetZoom(JNIEnv *env, jclass thiz, ID_TYPE id_camera, jint zoom) {
	jint result = JNI_ERR;
	ENTER();
	UVCCamera *camera = reinterpret_cast<UVCCamera *>(id_camera);
	if (LIKELY(camera)) {
		result = camera->setZoom(zoom);
	}
	RETURN(result, jint);
}

} /* extern "C" */
