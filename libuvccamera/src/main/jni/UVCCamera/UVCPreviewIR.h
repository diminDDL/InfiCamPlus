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

#include "libUVCCamera.h"
#include "UVCPreviewIR.h"
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

typedef struct {
	jmethodID onReceiveTemperature;
} Fields_iTemperatureCallback;

struct irBuffer//使用专业级图像算法所需要的缓存
{
    size_t** midVar;
    unsigned char* destBuffer;
};

class UVCPreviewIR {
private:
	inline const bool isRunning() const;
	inline const bool isComputed() const;
    unsigned short *mInitData;
	uvc_device_handle_t *mDeviceHandle;
	ANativeWindow *mPreviewWindow;
	volatile bool mIsRunning;
	int requestWidth, requestHeight, requestMode;
	int requestMinFps, requestMaxFps;
	float requestBandwidth;
	int frameWidth, frameHeight;
	int frameMode;
	unsigned char *OutBuffer;//使用完的buffer
	unsigned char *HoldBuffer;// 充满新数据的buffer
	unsigned char *RgbaOutBuffer;
	unsigned char *RgbaHoldBuffer;
	size_t frameBytes;
	pthread_t preview_thread;
	pthread_mutex_t preview_mutex;
	pthread_cond_t preview_sync;
	void signal_receive_frame_data();
	int previewFormat;
	int copyToSurface(uint8_t *frameData, ANativeWindow **window);

    volatile bool mIsComputed;
	Fields_iTemperatureCallback iTemperatureCallback;
// improve performance by reducing memory allocation
    irBuffer* irBuffers;
//ir temperature
    bool mIsTemperaturing;
	pthread_t temperature_thread;
	pthread_mutex_t temperature_mutex;
	pthread_cond_t temperature_sync;
	jobject mTemperatureCallbackObj;
    static void *temperature_thread_func(void *vptr_args);
    void do_temperature(JNIEnv *env);
    void do_temperature_callback(JNIEnv *env, uint8_t *frameData);

	//ir temp para
    int frameNumber;
    /**
     *temperatureTable:温度映射表
     */
    float temperatureTable[16384];
    bool isNeedWriteTable;
    int mTypeOfPalette;
	//测温相关参数，详见thermometry.h
    int rangeMode;
    float floatFpaTmp;
    float correction;
    float Refltmp;
    float Airtmp;
    float humi;
    float emiss;
    unsigned short distance;
    int cameraLens;
    float shutterFix;
	//end -测温相关参数

    char sn[32];//camera序列码
    char cameraSoftVersion[16];//camera软件版本
    unsigned short shutTemper;
    unsigned short coreTemper;

    float mCbTemper[640*512+10] ;
    unsigned short detectAvg;
    unsigned short fpaTmp;
    unsigned short maxx1;
    unsigned short maxy1;
    unsigned short t_max;
    unsigned short minx1;
    unsigned short miny1;
    unsigned short t_min;
    unsigned short t_avg;
    unsigned char paletteIronRainbow[65536 * 3];
    unsigned char palette3[256*3];//256*3 彩虹1
    unsigned char paletteRainbow[65536 * 3];
    unsigned char paletteHighRainbow[65536 * 3];
    unsigned char paletteHighContrast[347 *3];//448*3 高对比彩虹
    unsigned char UserPalette[256*3];

	void clearDisplay();
	static void uvc_preview_frame_callback(uint8_t *frame, void *vptr_args,size_t hold_bytes);
	static void *preview_thread_func(void *vptr_args);
	int prepare_preview(uvc_stream_ctrl_t *ctrl);
	void do_preview(uvc_stream_ctrl_t *ctrl);
	void draw_preview_one(uint8_t* frameData, ANativeWindow **window, convFunc_t func, int pixelBytes);

public:
	static const int START = 1;  // #1
	static const int STOP = 2;

	UVCPreviewIR();
	UVCPreviewIR(uvc_device_handle_t *devh);
	~UVCPreviewIR();
    void whenShutRefresh();
	int setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth);
	int setPreviewDisplay(ANativeWindow *preview_window);
	int setTemperatureCallback(JNIEnv *env, jobject temperature_callback_obj);
	int startPreview();
	int stopPreview();
	int stopTemp();
	int startTemp();
	void changePalette(int typeOfPalette);
	void setTempRange(int range);
	void setShutterFix(float mShutterFix);
	void setCameraLens(int mCameraLens);
	int getByteArrayTemperaturePara(uint8_t* para);
	void setUserPalette(uint8_t* palette,int typeOfPalette);
};

#endif /* UVCPREVIEW_IR_H_ */
