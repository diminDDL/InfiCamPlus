/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: UVCCamera.h
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

#pragma interface

#ifndef UVCCAMERA_H_
#define UVCCAMERA_H_

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <android/native_window.h>
#include "UVCPreviewIR.h"

class UVCCamera {
	uvc_context_t *mContext;
	int mFd;
	uvc_device_t *mDevice;
	uvc_device_handle_t *mDeviceHandle;
	UVCPreviewIR *mPreview;

public:
	UVCCamera();
	~UVCCamera();

	int connect(int vid, int pid, int fd, int busnum, int devaddr, const char *usbfs);
	int release();
	void whenShutRefresh();
    void SetUserPalette(uint8_t* palette,int typeOfPalette);
	char *getSupportedSize();
	int setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth = DEFAULT_BANDWIDTH);
	int setPreviewDisplay(ANativeWindow *preview_window);
	int setTemperatureCallback(JNIEnv *env, jobject temperature_callback_obj);
	int startPreview();
	int stopPreview();
	int setZoom(int zoom);

	void changePalette(int typeOfPalette);
	void setTempRange(int range);
	void setShutterFix(float mShutterFix);
	void setCameraLens(int mCameraLens);
	int getByteArrayTemperaturePara(uint8_t* para);

	char *getSupportedSize(const uvc_device_handle_t *deviceHandle);
};

#endif /* UVCCAMERA_H_ */
