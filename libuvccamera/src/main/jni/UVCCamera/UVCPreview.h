/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: UVCPreview.h
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

#ifndef UVCPREVIEW_H_
#define UVCPREVIEW_H_
#include "libUVCCamera.h"
#include <pthread.h>
#include <android/native_window.h>

#define DEFAULT_PREVIEW_WIDTH 640
#define DEFAULT_PREVIEW_HEIGHT 480
#define DEFAULT_PREVIEW_FPS_MIN 1
#define DEFAULT_PREVIEW_FPS_MAX 30
#define DEFAULT_PREVIEW_MODE 0
#define DEFAULT_BANDWIDTH 1.0f

typedef uvc_error_t (*convFunc_t)(uvc_frame_t *in, uvc_frame_t *out);

#define PIXEL_FORMAT_RAW 0		// same as PIXEL_FORMAT_YUV
#define PIXEL_FORMAT_YUV 1
#define PIXEL_FORMAT_RGB565 2
#define PIXEL_FORMAT_RGBX 3
#define PIXEL_FORMAT_YUV20SP 4
#define PIXEL_FORMAT_NV21 5		// YVU420SemiPlanar

// for callback to Java object
typedef struct {
	jmethodID onFrame;
} Fields_iframecallback;
typedef struct {
	jmethodID onReceiveTemperature;
} Fields_iTemperatureCallback;
class UVCPreview {

public:
	UVCPreview(){};
	~UVCPreview(){};
	static const int START = 1;  // #1
    static const int STOP = 2;
    virtual void whenShutRefresh()=0;
	virtual int setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth)=0;
	virtual int setPreviewDisplay(ANativeWindow *preview_window)=0;
	virtual int setFrameCallback(JNIEnv *env, jobject frame_callback_obj, int pixel_format)=0;
	virtual int setTemperatureCallback(JNIEnv *env, jobject temperature_callback_obj)=0;
	virtual int startPreview()=0;
	virtual int stopPreview()=0;
	virtual int stopTemp()=0;
	virtual int startTemp()=0;
	virtual void changePalette(int typeOfPalette)=0;
	virtual void setTempRange(int range)=0;
	virtual void setShutterFix(float mShutterFix)=0;
	virtual void setCameraLens(int mCameraLens)=0;
	virtual int getByteArrayPicture(uint8_t* frame)=0;
	virtual int getByteArrayTemperaturePara(uint8_t* para)=0;
	virtual void setUserPalette(uint8_t* palette,int typeOfPalette)=0;

};

#endif /* UVCPREVIEW_H_ */
