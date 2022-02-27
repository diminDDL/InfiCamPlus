/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: UVCPreviewIR.cpp
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

#if 1	// set 1 if you don't need debug log
#ifndef LOG_NDEBUG
#define	LOG_NDEBUG		// w/o LOGV/LOGD/MARK
#endif
#undef USE_LOGALL
#else
#define USE_LOGALL
	#undef LOG_NDEBUG
//	#undef NDEBUG
#endif

#define	LOCAL_DEBUG 0
#define PREVIEW_PIXEL_BYTES 4	// RGBA/RGBX
#define OUTPUTMODE 4

#include <stdlib.h>
#include <linux/time.h>
#include <unistd.h>
#include <math.h>
#include "utilbase.h"
#include "UVCPreviewIR.h"
#include "libuvc_internal.h"
#include "InfiFrame.h"
#include "../libuvc/include/libuvc/libuvc.h"

JavaVM *savedVm; // TODO this is lame

extern "C" {
	jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	#if LOCAL_DEBUG
		LOGD("JNI_OnLoad");
	#endif

		JNIEnv *env;
		if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
			return JNI_ERR;
		}
		// register native methods
		savedVm = vm;
	#if LOCAL_DEBUG
		LOGD("JNI_OnLoad:finshed:result=%d", result);
	#endif
		return JNI_VERSION_1_6;
	}
}

void UVCPreviewIR::connect2() {
	OutBuffer = new unsigned char[width*(height)*2];
	HoldBuffer = new unsigned char[width*(height)*2];
	RgbaOutBuffer = new unsigned char[width*(height-4)*4];
	RgbaHoldBuffer = new unsigned char[width*(height-4)*4];
}

UVCPreviewIR::UVCPreviewIR() :	mPreviewWindow(NULL),
								  previewFormat(WINDOW_FORMAT_RGBX_8888),
								  mIsRunning(false),
								  isNeedWriteTable(true),
								  mTemperatureCallbackObj(NULL) {

	// TODO (netman) This is temporary generating a palette thing, dunno what the plan is yet, but it doesn't belong here.
	for (int i = 0; i + 4 <= sizeof(ic.palette); i += 4) {
		double x = (double) i / (double) sizeof(ic.palette);
		((uint8_t *) ic.palette)[i + 0] = round(255 * sqrt(x));
		((uint8_t *) ic.palette)[i + 1] = round(255 * pow(x, 3));
		((uint8_t *) ic.palette)[i + 2] = round(255 * fmax(0, sin(2 * M_PI * x)));
		((uint8_t *) ic.palette)[i + 3] = 255;
	}

	// TODO (netman) This is temporary generating a palette thing, dunno what the plan is yet, but it doesn't belong here.
	// TODO add partial (0-270 degrees) rainbow, where cold is blue and red is hot
	/*for (int i = 0; i + 4 <= sizeof(ic.palette); i += 4) {
		double h = 360.0 - (double) i / (double) sizeof(ic.palette) * 360.0;
		double x = (1 - abs(fmod(h / 60.0, 2) - 1));
		double r, g, b;
		if (h >= 0 && h < 60)
			r = 1, g = x, b = 0;
		else if(h >= 60 && h < 120)
			r = x, g = 1, b = 0;
		else if(h >= 120 && h < 180)
			r = 0, g = 1, b = x;
		else if(h >= 180 && h < 240)
			r = 0, g = x, b = 1;
		else if(h >= 240 && h < 300)
			r = x, g = 0, b = 1;
		else r = 1, g = 0, b = x;
		((uint8_t *) ic.palette)[i + 0] = round(255 * r);
		((uint8_t *) ic.palette)[i + 1] = round(255 * g);
		((uint8_t *) ic.palette)[i + 2] = round(255 * b);
		((uint8_t *) ic.palette)[i + 3] = 255;
	}*/

	mIsComputed=true;
	mTypeOfPalette=0;
	rangeMode=120;
	cameraLens=130;//130;//镜头大小:目前支持两种，68：使用6.8mm镜头，130：使用13mm镜头,默认130。
	memset(sn, 0, 32);
	memset(cameraSoftVersion, 0, 16);
	memset(UserPalette,0,3*256*sizeof(unsigned char));
	pthread_mutex_init(&preview_mutex, NULL);

}

UVCPreviewIR::UVCPreviewIR(uvc_device_handle_t *devh)
:	mPreviewWindow(NULL),
	previewFormat(WINDOW_FORMAT_RGBX_8888),
	mIsRunning(false),
	isNeedWriteTable(true),
	mTemperatureCallbackObj(NULL)
{
	ENTER();
	mIsComputed=true;
    mTypeOfPalette=0;
    rangeMode=120;
    cameraLens=130;//130;//镜头大小:目前支持两种，68：使用6.8mm镜头，130：使用13mm镜头,默认130。
    memset(sn, 0, 32);
    memset(cameraSoftVersion, 0, 16);
    memset(UserPalette,0,3*256*sizeof(unsigned char));
    pthread_mutex_init(&preview_mutex, NULL);
	EXIT();
}

UVCPreviewIR::~UVCPreviewIR() {
	ENTER();
	pthread_mutex_destroy(&preview_mutex);
////LOGE("~UVCPreviewIR() 0");
	if (mPreviewWindow)
		ANativeWindow_release(mPreviewWindow);
	mPreviewWindow = NULL;
	SAFE_DELETE(mInitData);
	SAFE_DELETE_ARRAY(OutBuffer);
	SAFE_DELETE_ARRAY(HoldBuffer);
	SAFE_DELETE_ARRAY(RgbaOutBuffer);
	SAFE_DELETE_ARRAY(RgbaHoldBuffer);
}

inline const bool UVCPreviewIR::isRunning() const { return mIsRunning; }
inline const bool UVCPreviewIR::isComputed() const { return mIsComputed; }

int UVCPreviewIR::setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth) {
	return 0;
}

int UVCPreviewIR::setPreviewDisplay(ANativeWindow *preview_window) {
	ENTER();
	////LOGE("setPreviewDisplay");
	pthread_mutex_lock(&preview_mutex);
	{
		if (mPreviewWindow != preview_window) {
			if (mPreviewWindow)
				ANativeWindow_release(mPreviewWindow);
			mPreviewWindow = preview_window;
			if (LIKELY(mPreviewWindow)) {
				ANativeWindow_setBuffersGeometry(mPreviewWindow,
					width, height-4, previewFormat);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	RETURN(0, int);
}

int UVCPreviewIR::setTemperatureCallback(JNIEnv *env,jobject temperature_callback_obj) {
	ENTER();
	//pthread_create(&temperature_thread, NULL, temperature_thread_func, (void *)this);
	////LOGE("setTemperatureCallback01");
	pthread_mutex_lock(&preview_mutex);
	{
		if (!env->IsSameObject(mTemperatureCallbackObj, temperature_callback_obj))	{
		////LOGE("setTemperatureCallback !env->IsSameObject");
				iTemperatureCallback.onReceiveTemperature = NULL;
    			if (mTemperatureCallbackObj) {
    			////LOGE("setTemperatureCallback !env->IsSameObject mTemperatureCallbackObj1");
    				env->DeleteGlobalRef(mTemperatureCallbackObj);
    			}
    			mTemperatureCallbackObj = temperature_callback_obj;
    			if (mTemperatureCallbackObj) {
    				// get method IDs of Java object for callback
    				////LOGE("setTemperatureCallback !env->IsSameObject mTemperatureCallbackObj2");
    				jclass clazz = env->GetObjectClass(mTemperatureCallbackObj);
    				if (LIKELY(clazz)) {
    					iTemperatureCallback.onReceiveTemperature = env->GetMethodID(clazz,
    						"onReceiveTemperature",	"([F)V");
    				////LOGE("setTemperatureCallback !env->IsSameObject mTemperatureCallbackObj3");
    				} else {
    					////LOGE("failed to get object class");
    				}
    				env->ExceptionClear();
    				if (!iTemperatureCallback.onReceiveTemperature) {
    					////LOGE("Can't find IFrameCallback#onFrame");
    					env->DeleteGlobalRef(temperature_callback_obj);
    					mTemperatureCallbackObj = temperature_callback_obj = NULL;
    				}
    			}

		}
	}
    pthread_mutex_unlock(&preview_mutex);
	RETURN(0, int);
}

void UVCPreviewIR::clearDisplay() {
	ENTER();
////LOGE("clearDisplay");
	ANativeWindow_Buffer buffer;
	pthread_mutex_lock(&preview_mutex);
	{
		if (LIKELY(mPreviewWindow)) {
			if (LIKELY(ANativeWindow_lock(mPreviewWindow, &buffer, NULL) == 0)) {
				uint8_t *dest = (uint8_t *)buffer.bits;
				const size_t bytes = buffer.width * PREVIEW_PIXEL_BYTES;
				const int stride = buffer.stride * PREVIEW_PIXEL_BYTES;
				for (int i = 0; i < buffer.height; i++) {
					memset(dest, 0, bytes);
					dest += stride;
				}
				ANativeWindow_unlockAndPost(mPreviewWindow);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);

	EXIT();
}

int UVCPreviewIR::startPreview() {
	ENTER();
////LOGE("startPreview");

	pthread_mutex_lock(&preview_mutex);
	if (LIKELY(mPreviewWindow)) {
		ANativeWindow_setBuffersGeometry(mPreviewWindow, width, height - 4, previewFormat);//ir软件384*292中，实质384*288图像数据，4行其他数据
		////LOGE("ANativeWindow_setBuffersGeometry:(%d,%d)", frameWidth, frameHeight);
	}
	pthread_mutex_unlock(&preview_mutex);
	ic.init(width, height, (cameraLens == 68) ? 3 : 1, rangeMode);
	LOGE("WH  %d %d", width, height);

	int result = 0;
    if (!isRunning())
	{
		mIsRunning = true;
		int ret = stream_start(uvc_preview_frame_callback, (void *) this);
		LOGE("RETERA %d %d %d", ret, width, height);
	}
	////LOGE("STARTPREVIEW RESULT2:%d",result);
	RETURN(result, int);
}

int UVCPreviewIR::stopPreview() {
	ENTER();
	////LOGE("stopPreview");
	bool b = isRunning();
	if (LIKELY(b)) {
		mIsRunning = false;
		stream_stop();
	}
	pthread_mutex_lock(&preview_mutex);
	if (mPreviewWindow) {
		ANativeWindow_release(mPreviewWindow);
		mPreviewWindow = NULL;
	}
	pthread_mutex_unlock(&preview_mutex);

    // end - 释放专业图像算法占用的资源
	RETURN(0, int);
}

void UVCPreviewIR::uvc_preview_frame_callback(struct uvc_frame *frame, void *vptr_args) {
    UVCPreviewIR *preview = reinterpret_cast<UVCPreviewIR *>(vptr_args);
    unsigned short* tmp_buf = (unsigned short*)frame;
    ////LOGE("uvc_preview_frame_callback00  tmp_buf:%d,%d,%d,%d",tmp_buf[384*144*4],tmp_buf[384*144*4+1],tmp_buf[384*144*4+2],tmp_buf[384*144*4+3]);

    ////LOGE("uvc_preview_frame_callback hold_bytes:%d,preview->frameBytes:%d",hold_bytes,preview->frameBytes);

    size_t frameBytes = preview->width * preview->height * 2;
    if(LIKELY( preview->isRunning()) && frame->data_bytes >= frameBytes)
    {
		//LOGE("uvc_preview_frame_callback01");
		memcpy(preview->OutBuffer, frame->data, frameBytes);
		//LOGE("uvc_preview_frame_callback02");
		/* swap the buffers org */
		uint8_t *tmp_buf = preview->OutBuffer;
		preview->OutBuffer = preview->HoldBuffer;
		preview->HoldBuffer = tmp_buf;

		preview->do_preview();
    }
}

void UVCPreviewIR::do_preview() {
	ENTER();

	pthread_mutex_lock(&preview_mutex);
	{

		// swap the buffers rgba
		uint8_t *tmp_buf = RgbaOutBuffer;
		RgbaOutBuffer = RgbaHoldBuffer;
		RgbaHoldBuffer = tmp_buf;

		// Update table.
		if(isNeedWriteTable) {
			ic.read_params((uint16_t *) HoldBuffer);
			ic.update_table((uint16_t *) HoldBuffer); // TODO remember the temperature thing also needs this.
			ic.read_version((uint16_t *) HoldBuffer, NULL, sn, cameraSoftVersion); // TODO need this?
			isNeedWriteTable=false;
			LOGE("myinfo %d %d %f %f %d %d", width, height, ic.temp(ic.temp_max), ic.temp(ic.temp_min), ic.temp_max, ic.temp_min);
		}

		ic.update((uint16_t *) HoldBuffer);
		// Draw preview.
		ic.palette_appy((uint16_t *) HoldBuffer, (uint32_t *) RgbaHoldBuffer);
		if (mPreviewWindow)
			copyToSurface(RgbaHoldBuffer, mPreviewWindow);

		JNIEnv *env;
		savedVm->AttachCurrentThread(&env, NULL);
		do_temperature_callback(env, HoldBuffer);
		savedVm->DetachCurrentThread();

		mIsComputed=true;
	}
	pthread_mutex_unlock(&preview_mutex);

	// TODO how to ensure stream_stop() is called?
	//stream_stop();

	EXIT();
}

// transfer specific frame data to the Surface(ANativeWindow)
int UVCPreviewIR::copyToSurface(uint8_t *frameData, ANativeWindow *window) {
	// ENTER();
	int result = 0;
	if (LIKELY(window)) {
		ANativeWindow_Buffer buffer;
		if (LIKELY(ANativeWindow_lock(window, &buffer, NULL) == 0)) {
			// source = frame data, destination = Surface(ANativeWindow)
			const uint8_t *src = frameData;
			const int src_w = width * PREVIEW_PIXEL_BYTES;
			const int dst_w = buffer.width * PREVIEW_PIXEL_BYTES;
			const int dst_step = buffer.stride * PREVIEW_PIXEL_BYTES;

			// set w and h to be the smallest of the two rectangles
			const int w = src_w < dst_w ? src_w : dst_w;
			const int h = height < buffer.height ? height : buffer.height;

			// transfer from frame data to the Surface
			uint8_t *dst = (uint8_t *) buffer.bits;
			for (int i = 0; i < h; ++i) {
				memcpy(dst, src, w);
				dst += dst_step;
				src += src_w;
			}

			ANativeWindow_unlockAndPost(window);
		} else {
			result = -1;
		}
	} else {
		LOGE("No window");
		result = -1;
	}
	return result; //RETURN(result, int);
}

/*
在这里可以返回测温相关参数
fix       float 0-3
Refltmp   float 3-7
Airtmp    float 7-11
humi      float 11-15
emiss     float 15-19
distance  ushort  20-21
version          112-127
*/
int UVCPreviewIR:: getByteArrayTemperaturePara(uint8_t* para) {
    uint8_t* TempPara = HoldBuffer + ic.s2_offset * 2 + 254;
	memcpy(para, TempPara, 128*sizeof(uint8_t));
	TempPara=TempPara-127*2+24*2;//version
	memcpy(para + 128 - 16, TempPara, 16 * sizeof(uint8_t));
	return true;
}

void UVCPreviewIR::do_temperature_callback(JNIEnv *env, uint8_t *frameData) {
	ENTER();

	float *temperatureData = mCbTemper;
	ic.temp((uint16_t *) HoldBuffer, temperatureData + 10);
	temperatureData[0] = ic.temp(ic.temp_center);
	temperatureData[1] = ic.temp_max_x;
	temperatureData[2] = ic.temp_max_y;
	temperatureData[3] = ic.temp(ic.temp_max);
	temperatureData[4] = ic.temp_min_x;
	temperatureData[5] = ic.temp_min_y;
	temperatureData[6] = ic.temp(ic.temp_min);
	temperatureData[7] = ic.temp(ic.temp_user[0]);
	temperatureData[8] = ic.temp(ic.temp_user[1]);
	temperatureData[9] = ic.temp(ic.temp_user[2]);

	jfloatArray mNCbTemper = env->NewFloatArray(width*(height-4)+10);
	env->SetFloatArrayRegion(mNCbTemper, 0, 10+width*(height-4), mCbTemper);
	if (mTemperatureCallbackObj != NULL) {
		env->CallVoidMethod(mTemperatureCallbackObj, iTemperatureCallback.onReceiveTemperature, mNCbTemper);
		env->ExceptionClear();
	}
	env->DeleteLocalRef(mNCbTemper);
	EXIT();
}

//打快门更新表
void UVCPreviewIR::whenShutRefresh() {
    pthread_mutex_lock(&preview_mutex);
    {
        isNeedWriteTable=true;
    }
    pthread_mutex_unlock(&preview_mutex); // TODO the other ones don't need a mutex?
}

void UVCPreviewIR::setUserPalette(uint8_t *palette, int typeOfPalette) {
	////LOGE("SetUserPalette OUT:%X\n",palette);
	memcpy(UserPalette,palette,3*256*sizeof(unsigned char));
	mTypeOfPalette=typeOfPalette;
}

void UVCPreviewIR::changePalette(int typeOfPalette) {
    ENTER();
    mTypeOfPalette=typeOfPalette;
    EXIT();
}

void UVCPreviewIR::setTempRange(int range) {
    ENTER();
    rangeMode = range; // TODO (netman) Shouldn't this also trigger isNeedWriteTable?
    EXIT();
}

void UVCPreviewIR::setShutterFix(float mShutterFix) {
    ENTER();
    shutterFix = mShutterFix; // TODO (netman) Shouldn't this also trigger isNeedWriteTable?
    EXIT();
}

void UVCPreviewIR::setCameraLens(int mCameraLens) {
    ENTER();
    cameraLens = mCameraLens; // TODO (netman) Shouldn't this also trigger isNeedWriteTable?
    //LOGE("setCameraLens:%d\n",cameraLens);
    EXIT();
}

char *UVCPreviewIR::getSupportedSize() {
	// TODO make something sensible to just return default or supported sizes instead
	char buf[256] = { 0 };
	snprintf(buf, sizeof(buf), "%dx%d", width, height);
	buf[sizeof(buf)-1] = '\0';
	RETURN(strdup(buf), char *);
}
