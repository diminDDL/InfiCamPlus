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
#include "InfiFrame.h"

#define DEFAULT_PREVIEW_WIDTH 640
#define DEFAULT_PREVIEW_HEIGHT 480
#define DEFAULT_PREVIEW_FPS_MIN 1
#define DEFAULT_PREVIEW_FPS_MAX 30
#define DEFAULT_PREVIEW_MODE 0
#define DEFAULT_BANDWIDTH 1.0f

#define PIXEL_FORMAT_RAW 0		// same as PIXEL_FORMAT_YUV
#define PIXEL_FORMAT_YUV 1
#define PIXEL_FORMAT_RGB565 2
#define PIXEL_FORMAT_RGBX 3
#define PIXEL_FORMAT_YUV20SP 4
#define PIXEL_FORMAT_NV21 5		// YVU420SemiPlanar

typedef struct {
	jmethodID onReceiveTemperature;
} Fields_iTemperatureCallback;

class UVCPreviewIR : public UVCDevice {
private:
	inline const bool isRunning() const;
	inline const bool isComputed() const;
    unsigned short *mInitData;
	uvc_device_handle_t *mDeviceHandle;
	ANativeWindow *mPreviewWindow;
	volatile bool mIsRunning;
	unsigned char *OutBuffer; // 使用完的buffer
	unsigned char *HoldBuffer; // 充满新数据的buffer
	unsigned char *RgbaOutBuffer;
	unsigned char *RgbaHoldBuffer;
	pthread_mutex_t preview_mutex;
	int previewFormat;

    volatile bool mIsComputed;
	Fields_iTemperatureCallback iTemperatureCallback;
	//ir temperature
	jobject mTemperatureCallbackObj;

	InfiFrame ic;

	int copyToSurface(uint8_t *frameData, ANativeWindow *window);
    void do_temperature_callback(JNIEnv *env, uint8_t *frameData);

	//ir temp para
    bool isNeedWriteTable;
    int mTypeOfPalette;
	//测温相关参数，详见thermometry.h
    int rangeMode;
    int cameraLens;
    float shutterFix; // TODO, also fpaFix
	//end -测温相关参数

    char sn[32];//camera序列码
    char cameraSoftVersion[16];//camera软件版本

    float mCbTemper[640*512+10] ;
    unsigned char UserPalette[256*3];

	void clearDisplay();
	static void uvc_preview_frame_callback(struct uvc_frame *frame, void *vptr_args);
	void do_preview();

public:
	UVCPreviewIR();
	~UVCPreviewIR();

	void connect2();
    void whenShutRefresh();
	int setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth);
	int setPreviewDisplay(ANativeWindow *preview_window);
	int setTemperatureCallback(JNIEnv *env, jobject temperature_callback_obj);
	int startPreview();
	int stopPreview();
	void changePalette(int typeOfPalette);
	void setTempRange(int range);
	void setShutterFix(float mShutterFix);
	void setCameraLens(int mCameraLens);
	int getByteArrayTemperaturePara(uint8_t* para);
	void setUserPalette(uint8_t* palette,int typeOfPalette);

	char *getSupportedSize();
};

#endif /* UVCPREVIEW_IR_H_ */
