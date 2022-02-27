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

#ifndef UVCPREVIEW_IR_H_
#define UVCPREVIEW_IR_H_

#include <jni.h>
#include "libusb.h"
#include "libuvc.h"
#include "utilbase.h"
#include "UVCDevice.h"
#include <pthread.h>
#include <android/native_window.h>
#include "InfiCam.h"

typedef struct {
	jmethodID onReceiveTemperature;
} Fields_iTemperatureCallback;

class UVCPreviewIR {
private:
	inline const bool isRunning() const;
    unsigned short *mInitData;
	ANativeWindow *mPreviewWindow;
	volatile bool mIsRunning;
	pthread_mutex_t preview_mutex;

	Fields_iTemperatureCallback iTemperatureCallback;
	//ir temperature
	jobject mTemperatureCallbackObj;

	int copyToSurface(uint8_t *frameData, ANativeWindow *window);

	//ir temp para
	//测温相关参数，详见thermometry.h
    int rangeMode;
    int cameraLens;
    float shutterFix; // TODO, also fpaFix
	//end -测温相关参数

    float mCbTemper[640*512+10] ;
    unsigned char UserPalette[256*3];

	static void uvc_preview_frame_callback(uint32_t *rgb, float *temp, uint16_t *raw, void *user_ptr);

public:
	InfiCam cam;

	UVCPreviewIR();
	~UVCPreviewIR();

    void whenShutRefresh();
	int setPreviewDisplay(ANativeWindow *preview_window);
	int setTemperatureCallback(JNIEnv *env, jobject temperature_callback_obj);
	int startPreview();
	int stopPreview();
	void changePalette(int typeOfPalette);
	void setTempRange(int range);
	void setCameraLens(int mCameraLens);
	int getByteArrayTemperaturePara(uint8_t* para);
	void setUserPalette(uint8_t* palette,int typeOfPalette);

	char *getSupportedSize();
};

#endif /* UVCPREVIEW_IR_H_ */
