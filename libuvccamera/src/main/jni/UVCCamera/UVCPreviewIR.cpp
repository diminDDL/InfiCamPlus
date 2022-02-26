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

UVCPreviewIR::UVCPreviewIR(){

}

UVCPreviewIR::UVCPreviewIR(uvc_device_handle_t *devh)
:	mPreviewWindow(NULL),
	mDeviceHandle(devh),
	requestWidth(DEFAULT_PREVIEW_WIDTH),
	requestHeight(DEFAULT_PREVIEW_HEIGHT),
	requestMinFps(DEFAULT_PREVIEW_FPS_MIN),
	requestMaxFps(DEFAULT_PREVIEW_FPS_MAX),
	requestMode(DEFAULT_PREVIEW_MODE),
	requestBandwidth(DEFAULT_BANDWIDTH),
	frameWidth(DEFAULT_PREVIEW_WIDTH),
	frameHeight(DEFAULT_PREVIEW_HEIGHT),
	previewFormat(WINDOW_FORMAT_RGBX_8888),
	mIsRunning(false),
	isNeedWriteTable(true),
	frameNumber(0),
	mIsTemperaturing(false),
	mTemperatureCallbackObj(NULL)
{
	ENTER();
	mIsComputed=true;
    mTypeOfPalette=0;
    rangeMode=120;
    floatFpaTmp=0;
    Refltmp=0;
    Airtmp=0;
    humi=0;
    cameraLens=130;//130;//镜头大小:目前支持两种，68：使用6.8mm镜头，130：使用13mm镜头,默认130。
    memset(sn, 0, 32);
    memset(cameraSoftVersion, 0, 16);
    memset(UserPalette,0,3*256*sizeof(unsigned char));
	pthread_cond_init(&preview_sync, NULL);
	pthread_mutex_init(&preview_mutex, NULL);
	pthread_cond_init(&temperature_sync,NULL);
	pthread_mutex_init(&temperature_mutex,NULL);
	EXIT();
}

UVCPreviewIR::~UVCPreviewIR() {
	ENTER();
////LOGE("~UVCPreviewIR() 0");
	if (mPreviewWindow)
		ANativeWindow_release(mPreviewWindow);
	mPreviewWindow = NULL;
	////LOGE("~UVCPreviewIR() 1");
	pthread_mutex_destroy(&preview_mutex);
	pthread_cond_destroy(&preview_sync);
	pthread_mutex_destroy(&temperature_mutex);
    pthread_cond_destroy(&temperature_sync);
    ////LOGE("~UVCPreviewIR() 8");
	EXIT();
}

inline const bool UVCPreviewIR::isRunning() const { return mIsRunning; }
inline const bool UVCPreviewIR::isComputed() const { return mIsComputed; }

int UVCPreviewIR::setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth) {
	ENTER();
	////LOGE("setPreviewSize");
	int result = 0;
	if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)) {
		requestWidth = width;
		requestHeight = height;
		requestMinFps = min_fps;
		requestMaxFps = max_fps;
		requestMode = mode;
		requestBandwidth = bandwidth;

		uvc_stream_ctrl_t ctrl;
		result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, &ctrl,
			!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
			requestWidth, requestHeight, requestMinFps, requestMaxFps);
	  ////LOGE("uvc_get_stream_ctrl_format_size_fps=%d", result);
	}
	RETURN(result, int);
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
					requestWidth, requestHeight-4, previewFormat);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	RETURN(0, int);
}

int UVCPreviewIR::setTemperatureCallback(JNIEnv *env,jobject temperature_callback_obj){
	ENTER();
	//pthread_create(&temperature_thread, NULL, temperature_thread_func, (void *)this);
	////LOGE("setTemperatureCallback01");
	pthread_mutex_lock(&temperature_mutex);
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
    pthread_mutex_unlock(&temperature_mutex);
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
	int result = EXIT_FAILURE;
    if (!isRunning())
	{
		mIsRunning = true;
		//pthread_mutex_lock(&preview_mutex);
		//{
		result = pthread_create(&preview_thread, NULL, preview_thread_func, (void *)this);
		////LOGE("STARTPREVIEW RESULT1:%d",result);
		//}
	//	pthread_mutex_unlock(&preview_mutex);
		if (UNLIKELY(result != EXIT_SUCCESS))
		 {
			////LOGE("UVCCamera::window does not exist/already running/could not create thread etc.");
			mIsRunning = false;
			pthread_mutex_lock(&preview_mutex);
			{
				pthread_cond_signal(&preview_sync);
			}
			pthread_mutex_unlock(&preview_mutex);
		}
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
		pthread_cond_signal(&preview_sync);
		//pthread_cond_signal(&capture_sync);
		//if (pthread_join(capture_thread, NULL) != EXIT_SUCCESS) {
		//	LOGW("UVCPreviewIR::terminate capture thread: pthread_join failed");
		//}
		if (pthread_join(preview_thread, NULL) != EXIT_SUCCESS)
		{
			////LOGE("UVCPreviewIR::terminate preview thread: pthread_join failed");
		}
		else
		{
		    ////LOGE("UVCPreviewIR::terminate preview thread: EXIT_SUCCESS");
		}
		if(mIsTemperaturing)
		{
		    mIsTemperaturing=false;
            if (pthread_join(temperature_thread, NULL) != EXIT_SUCCESS) {
                ////LOGE("UVCPreviewIR::terminate temperature_thread: pthread_join failed");
            }
            else
            {
                ////LOGE("UVCPreviewIR::terminate temperature_thread: pthread_join success");
            }
        }
		//clearDisplay();
	}
	pthread_mutex_lock(&preview_mutex);
	if (mPreviewWindow) {
		ANativeWindow_release(mPreviewWindow);
		mPreviewWindow = NULL;
	}
	pthread_mutex_unlock(&preview_mutex);

    SAFE_DELETE(mInitData);
	SAFE_DELETE_ARRAY(OutBuffer);
	SAFE_DELETE_ARRAY(HoldBuffer);
	SAFE_DELETE_ARRAY(RgbaOutBuffer);
	SAFE_DELETE_ARRAY(RgbaHoldBuffer);

    // end - 释放专业图像算法占用的资源
	RETURN(0, int);
}

void UVCPreviewIR::uvc_preview_frame_callback(struct uvc_frame *frame, void *vptr_args)
{
    //LOGE("uvc_preview_frame_callback00");
    UVCPreviewIR *preview = reinterpret_cast<UVCPreviewIR *>(vptr_args);
    unsigned short* tmp_buf = (unsigned short*)frame;
    ////LOGE("uvc_preview_frame_callback00  tmp_buf:%d,%d,%d,%d",tmp_buf[384*144*4],tmp_buf[384*144*4+1],tmp_buf[384*144*4+2],tmp_buf[384*144*4+3]);

    ////LOGE("uvc_preview_frame_callback hold_bytes:%d,preview->frameBytes:%d",hold_bytes,preview->frameBytes);

    size_t frameBytes = preview->requestWidth * preview->requestHeight * 2;
    if(LIKELY( preview->isRunning()) && frame->actual_bytes >= frameBytes)
    {
		//LOGE("uvc_preview_frame_callback01");
		memcpy(preview->OutBuffer, frame->data, frameBytes);
		//LOGE("uvc_preview_frame_callback02");
		/* swap the buffers org */
		uint8_t *tmp_buf = preview->OutBuffer;
		preview->OutBuffer = preview->HoldBuffer;
		preview->HoldBuffer = tmp_buf;
		pthread_cond_signal(&preview->preview_sync);
    }
    //LOGE("uvc_preview_frame_callback03");
}

void *UVCPreviewIR::preview_thread_func(void *vptr_args)
 {
    ////LOGE("preview_thread_func");
	int result;
	ENTER();
	UVCPreviewIR *preview = reinterpret_cast<UVCPreviewIR *>(vptr_args);
	if (LIKELY(preview))
	{
		uvc_stream_ctrl_t ctrl;
		result = preview->prepare_preview(&ctrl);
		if (LIKELY(!result))
		{
			preview->do_preview(&ctrl);
		}
	}
	PRE_EXIT();
	pthread_exit(NULL);
}

int UVCPreviewIR::prepare_preview(uvc_stream_ctrl_t *ctrl) {
////LOGE("prepare_preview");
	uvc_error_t result;
	ENTER();
    OutBuffer = new unsigned char[requestWidth*(requestHeight)*2];
    HoldBuffer = new unsigned char[requestWidth*(requestHeight)*2];
    RgbaOutBuffer = new unsigned char[requestWidth*(requestHeight-4)*4];
    RgbaHoldBuffer = new unsigned char[requestWidth*(requestHeight-4)*4];

    // TODO (netman) This is temporary generating a palette thing, dunno what the plan is yet, but it doesn't belong here.
	for (int i = 0; i + 3 <= sizeof(paletteIronRainbow); i += 3) {
		double x = (double) i / (double) sizeof(paletteIronRainbow);
		paletteIronRainbow[i + 0] = round(255 * sqrt(x));
		paletteIronRainbow[i + 1] = round(255 * pow(x, 3));
		paletteIronRainbow[i + 2] = round(255 * fmax(0, sin(2 * M_PI * x)));
	}

	// TODO (netman) This is temporary generating a palette thing, dunno what the plan is yet, but it doesn't belong here.
	// TODO add partial (0-270 degrees) rainbow, where cold is blue and red is hot
	for (int i = 0; i + 3 <= sizeof(paletteRainbow); i += 3) {
		double h = 360.0 - (double) i / (double) sizeof(paletteRainbow) * 360.0;
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
		paletteRainbow[i + 0] = round(255 * r);
		paletteRainbow[i + 1] = round(255 * g);
		paletteRainbow[i + 2] = round(255 * b);
	}

	memcpy(palette3, paletteRainbow, sizeof(palette3));
	memcpy(paletteHighRainbow, paletteRainbow, sizeof(paletteRainbow));
	memcpy(paletteHighContrast, paletteIronRainbow, sizeof(palette3));


	// TODO this is the new one
	for (int i = 0; i + 4 <= sizeof(ic.palette); i += 4) {
		double x = (double) i / (double) sizeof(ic.palette);
		((uint8_t *) ic.palette)[i + 0] = round(255 * sqrt(x));
		((uint8_t *) ic.palette)[i + 1] = round(255 * pow(x, 3));
		((uint8_t *) ic.palette)[i + 2] = round(255 * fmax(0, sin(2 * M_PI * x)));
		((uint8_t *) ic.palette)[i + 3] = 1;
	}


    mInitData=new unsigned short[requestWidth*(requestHeight-4)+10];
	result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, ctrl,
		!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
		requestWidth, requestHeight, requestMinFps, requestMaxFps
	);
	////LOGE("re:%d,frameSize=(%d,%d)@%d,%d",result, requestWidth, requestHeight, requestMinFps,requestMaxFps);
	if (LIKELY(!result))
	{
        #if LOCAL_DEBUG
                uvc_print_stream_ctrl(ctrl, stderr);
        #endif
		uvc_frame_desc_t *frame_desc;
		result = uvc_get_frame_desc(mDeviceHandle, ctrl, &frame_desc);
		if (LIKELY(!result))
		 {
			frameWidth = frame_desc->wWidth;
			frameHeight = frame_desc->wHeight;
			////LOGE("frameSize=(%d,%d)@%s", frameWidth, frameHeight, (!requestMode ? "YUYV" : "MJPEG"));
			pthread_mutex_lock(&preview_mutex);
			if (LIKELY(mPreviewWindow)) {
				ANativeWindow_setBuffersGeometry(mPreviewWindow,
					frameWidth, frameHeight-4, previewFormat);//ir软件384*292中，实质384*288图像数据，4行其他数据
			////LOGE("ANativeWindow_setBuffersGeometry:(%d,%d)", frameWidth, frameHeight);
			}
			pthread_mutex_unlock(&preview_mutex);
		} else {
			frameWidth = requestWidth;
			frameHeight = requestHeight;
		}
	}
	else
	 {
		////LOGE("could not negotiate with camera:err=%d", result);
	 }
	ic.init(requestWidth, requestHeight, (cameraLens == 68) ? 3 : 1, rangeMode);
	RETURN(result, int);
}

void UVCPreviewIR::do_preview(uvc_stream_ctrl_t *ctrl) {
	ENTER();
    //////LOGE("do_preview");
	uvc_error_t result = uvc_start_streaming_bandwidth(mDeviceHandle, ctrl, uvc_preview_frame_callback, (void *)this, requestBandwidth, 0);
	if (LIKELY(!result)) {
		//pthread_create(&capture_thread, NULL, capture_thread_func, (void *)this);
	    //pthread_create(&temperature_thread, NULL, temperature_thread_func, (void *)this);
        #if LOCAL_DEBUG
		LOGI("Streaming...");
        #endif
	    // yuvyv mode
		for ( ; LIKELY(isRunning()) ; )
		{
            //LOGE("do_preview0");
            pthread_mutex_lock(&preview_mutex);
            {
                //LOGE("waitPreviewFrame");
                pthread_cond_wait(&preview_sync, &preview_mutex);
                //LOGE("waitPreviewFrame02");

				//if(OutPixelFormat==3)//RGBA 32bit输出
				//  {

				// swap the buffers rgba
				uint8_t *tmp_buf = RgbaOutBuffer;
				RgbaOutBuffer = RgbaHoldBuffer;
				RgbaHoldBuffer = tmp_buf;

				ic.update((uint16_t *) HoldBuffer);
				ic.read_version((uint16_t *) HoldBuffer, NULL, sn, cameraSoftVersion);
				Refltmp = ic.temp_reflected;
				Airtmp = ic.temp_air;
				humi = ic.humidity;
				t_max = ic.temp_max;
				t_min = ic.temp_min;

				draw_preview_one(HoldBuffer, mPreviewWindow);
				mIsComputed=true;
               // }
            }
            pthread_mutex_unlock(&preview_mutex);
            if (mTemperatureCallbackObj && mIsTemperaturing)
                pthread_cond_signal(&temperature_sync);

	    }

		//pthread_cond_signal(&capture_sync);


#if LOCAL_DEBUG
		LOGI("preview_thread_func:wait for all callbacks complete");
#endif
		uvc_stop_streaming(mDeviceHandle);
#if LOCAL_DEBUG
		LOGI("Streaming finished");
#endif
	} else {
		uvc_perror(result, "failed start_streaming");
	}

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
			const int src_w = requestWidth * PREVIEW_PIXEL_BYTES;
			const int dst_w = buffer.width * PREVIEW_PIXEL_BYTES;
			const int dst_step = buffer.stride * PREVIEW_PIXEL_BYTES;

			// set w and h to be the smallest of the two rectangles
			const int w = src_w < dst_w ? src_w : dst_w;
			const int h = frameHeight < buffer.height ? frameHeight : buffer.height;

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
		result = -1;
	}
	return result; //RETURN(result, int);
}

void UVCPreviewIR::draw_preview_one(uint8_t *frameData, ANativeWindow *window) {
	unsigned short *tmp_buf = (unsigned short*) frameData;
	//8005模式下yuyv转rgba
	//uvc_yuyv2rgbx2(tmp_buf, RgbaHoldBuffer,requestWidth,requestHeight);

	// TODO we should generate a pallette that actually matches the temperature curve at each shutter.
	//   Or use the calculated temperature to index a pallette.

	/**
	 * 线性图像算法
	 * 图像效果不及专业级算法，但是处理效率快，对主频几乎没要求
	 *
	 */
	int avgSubMin= (t_avg - t_min) > 0 ? (t_avg - t_min) : 1;
	int maxSubAvg= (t_max - t_avg) > 0 ? (t_max - t_avg) : 1;
	int ro1 = (t_avg - t_min) > 97 ? 97 : (t_avg - t_min);
	int ro2 = (t_max - t_avg) > 157 ? 157 : (t_max - t_avg);
	int span = t_max - t_min;
	switch (mTypeOfPalette) {
		case 0:
			for(int i = 0; i < requestWidth * (requestHeight - 4); i++) {
				int gray = (tmp_buf[i] - t_min) * 255 / span;
				RgbaHoldBuffer[4 * i + 0] = (unsigned char) gray;
				RgbaHoldBuffer[4 * i + 1] = (unsigned char) gray;
				RgbaHoldBuffer[4 * i + 2] = (unsigned char) gray;
				RgbaHoldBuffer[4 * i + 3] = 1;
			}
			break;
		case 1:
			for(int i = 0; i < requestWidth * (requestHeight - 4); i++) {
				int gray = 255 - (tmp_buf[i] - t_min) * 255 / span;
				RgbaHoldBuffer[4 * i + 0] = (unsigned char) gray;
				RgbaHoldBuffer[4 * i + 1] = (unsigned char) gray;
				RgbaHoldBuffer[4 * i + 2] = (unsigned char) gray;
				RgbaHoldBuffer[4 * i + 3] = 1;
			}
			break;
		case 2:
			for(int i = 0; i < requestWidth * (requestHeight - 4); i++) {
				int gray = (int)(tmp_buf[i] - t_min) * (sizeof(paletteIronRainbow) / 3 - 1) / span;
				int paletteNum = 3 * gray;
				if (gray < sizeof(paletteIronRainbow) / 3) { // TODO this is probly slow and we should range check all of em
					RgbaHoldBuffer[4 * i + 0] = (unsigned char) paletteIronRainbow[paletteNum + 0];
					RgbaHoldBuffer[4 * i + 1] = (unsigned char) paletteIronRainbow[paletteNum + 1];
					RgbaHoldBuffer[4 * i + 2] = (unsigned char) paletteIronRainbow[paletteNum + 2];
					RgbaHoldBuffer[4 * i + 3] = 1;
				}
			}
			break;
		case 3:
		  ro1= (t_avg - t_min) > 132 ? 132 : (t_avg - t_min);
		  ro2= (t_max - t_avg) > 214 ? 214 : (t_max - t_avg);
		  for(int i=0; i<requestHeight-4; i++)
			{
				for(int j=0; j<requestWidth; j++)
				{
					//printf("i:%d,j:%d\n",i,j);
					//黑白：灰度值0-254单通道。 paletteIronRainbow：（0-254）×3三通道。两个都是255，所以使用254
					int gray=0;
					 if(tmp_buf[i*requestWidth+j] > t_avg)
					 {
						 gray = (int)(ro2 * (tmp_buf[i*requestWidth+j] - t_avg) / maxSubAvg + 132);
					 }
					 else
					 {
						 gray = (int)(ro1 * (tmp_buf[i*requestWidth+j] - t_avg) / avgSubMin + 132);
					 }
					int paletteNum=3*gray;
					RgbaHoldBuffer[4*(i*requestWidth+j)]=(unsigned char)paletteHighContrast[paletteNum];
					RgbaHoldBuffer[4*(i*requestWidth+j)+1]=(unsigned char)paletteHighContrast[paletteNum+1];
					RgbaHoldBuffer[4*(i*requestWidth+j)+2]=(unsigned char)paletteHighContrast[paletteNum+2];
					RgbaHoldBuffer[4*(i*requestWidth+j)+3]=1;
				}
			}
			break;
		case 4:
			for(int i = 0; i < requestWidth * (requestHeight - 4); i++) {
				int gray = (int)(tmp_buf[i] - t_min) * (sizeof(paletteRainbow) / 3 - 1) / span;
				int paletteNum = 3 * gray;
				RgbaHoldBuffer[4 * i + 0] = (unsigned char) paletteRainbow[paletteNum + 0];
				RgbaHoldBuffer[4 * i + 1] = (unsigned char) paletteRainbow[paletteNum + 1];
				RgbaHoldBuffer[4 * i + 2] = (unsigned char) paletteRainbow[paletteNum + 2];
				RgbaHoldBuffer[4 * i + 3] = 1;
			}
			break;
		 case 5:
			ro1= (t_avg - t_min) > 97 ? 97 : (t_avg - t_min);
			ro2= (t_max - t_avg) > 158 ? 158 : (t_max - t_avg);
		   for(int i=0; i<requestHeight-4; i++)
			 {
				 for(int j=0; j<requestWidth; j++)
				 {
					 //printf("i:%d,j:%d\n",i,j);
					 //黑白：灰度值0-254单通道。 paletteIronRainbow：（0-254）×3三通道。两个都是255，所以使用254
					  int gray=0;
					  if(tmp_buf[i*requestWidth+j] > t_avg)
					  {
						  gray = (int)(ro2 * (tmp_buf[i*requestWidth+j] - t_avg) / maxSubAvg + 97);
					  }
					  else
					  {
						  gray = (int)(ro1 * (tmp_buf[i*requestWidth+j] - t_avg) / avgSubMin + 97);
					  }
					 int paletteNum=3*gray;
					 RgbaHoldBuffer[4*(i*requestWidth+j)]=(unsigned char)palette3[paletteNum];
					 RgbaHoldBuffer[4*(i*requestWidth+j)+1]=(unsigned char)palette3[paletteNum+1];
					 RgbaHoldBuffer[4*(i*requestWidth+j)+2]=(unsigned char)palette3[paletteNum+2];
					 RgbaHoldBuffer[4*(i*requestWidth+j)+3]=1;
				 }
			 }
		 break;
		 case 6:
		 ro1= (t_avg - t_min) > 97 ? 97 : (t_avg - t_min);
		 ro2= (t_max - t_avg) > 158 ? 158 : (t_max - t_avg);
		  for(int i=0; i<requestHeight-4; i++)
			 {
				 for(int j=0; j<requestWidth; j++)
				 {
					 //printf("i:%d,j:%d\n",i,j);
					 //黑白：灰度值0-254单通道。 paletteIronRainbow：（0-254）×3三通道。两个都是255，所以使用254
					 int gray=0;
					   if(tmp_buf[i*requestWidth+j] > t_avg)
					   {
						   gray = (int)(ro2 * (tmp_buf[i*requestWidth+j] - t_avg) / maxSubAvg + 97);
					   }
					   else
					   {
						   gray = (int)(ro1 * (tmp_buf[i*requestWidth+j] - t_avg) / avgSubMin + 97);
					   }
					  int paletteNum=3*gray;
					 RgbaHoldBuffer[4*(i*requestWidth+j)]=(unsigned char)UserPalette[paletteNum];
					 RgbaHoldBuffer[4*(i*requestWidth+j)+1]=(unsigned char)UserPalette[paletteNum+1];
					 RgbaHoldBuffer[4*(i*requestWidth+j)+2]=(unsigned char)UserPalette[paletteNum+2];
					 RgbaHoldBuffer[4*(i*requestWidth+j)+3]=1;
				 }
			 }
		  break;
	 }
	ic.palette_appy((uint16_t *) tmp_buf, (uint32_t *) RgbaHoldBuffer, requestWidth * 96);

	if (LIKELY(window))
		copyToSurface(RgbaHoldBuffer, window);
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

int UVCPreviewIR::stopTemp() {
    ENTER();
	pthread_mutex_lock(&temperature_mutex);
	{
		if (isRunning() && mIsTemperaturing)
		 {
	        ////LOGE("stopTemp");
			mIsTemperaturing = false;
			pthread_cond_signal(&temperature_sync);
			pthread_cond_wait(&temperature_sync, &temperature_mutex);	// wait finishing Temperatur
		}
	}
	pthread_mutex_unlock(&temperature_mutex);
    if (pthread_join(temperature_thread, NULL) != EXIT_SUCCESS)
    {
        ////LOGE("UVCPreviewIR::stopTemp temperature_thread: pthread_join failed");
    }
    else
    {
        ////LOGE("UVCPreviewIR::stopTemp temperature_thread: pthread_join success");
    }
	RETURN(0, int);
}

int UVCPreviewIR::startTemp() {
	ENTER();
	pthread_mutex_lock(&temperature_mutex);
	{
		if (isRunning()&&(!mIsTemperaturing))
		 {
	        ////LOGE("startTemp");
			mIsTemperaturing = true;
		 }
	}
	pthread_mutex_unlock(&temperature_mutex);
	if(pthread_create(&temperature_thread, NULL, temperature_thread_func, (void *)this)==0)
	{
	    ////LOGE("UVCPreviewIR::startTemp temperature_thread: pthread_create success");
	}
	else
	{
	    ////LOGE("UVCPreviewIR::startTemp temperature_thread: pthread_create failed");
	}
	RETURN(0, int);
}

//======================================================================
/*
 * thread function for ir
 * @param vptr_args pointer to UVCPreviewIR instance
 */
// static
void *UVCPreviewIR::temperature_thread_func(void *vptr_args) {
	int result;
    ////LOGE("temperature_thread_func");
	ENTER();
	UVCPreviewIR *preview = reinterpret_cast<UVCPreviewIR *>(vptr_args);
	if (LIKELY(preview))
	{
        JavaVM *vm = savedVm;
		JNIEnv *env;
		//attach to JavaVM
		vm->AttachCurrentThread(&env, NULL);
		////LOGE("temperature_thread_func do_temperature");
		preview->do_temperature(env);	// never return until finish previewing
		//detach from JavaVM
		vm->DetachCurrentThread();
		MARK("DetachCurrentThread");
	}
	PRE_EXIT();
	pthread_exit(NULL);
}

/**
 * the actual function for temperature
 */
void UVCPreviewIR::do_temperature(JNIEnv *env) {
	ENTER();
    ////LOGE("do_temperature mIsTemperaturing:%d",mIsTemperaturing);
	 for (;isRunning()&&mIsTemperaturing;)
    {

        pthread_mutex_lock(&temperature_mutex);
        {
            ////LOGE("do_temperature01");
            pthread_cond_wait(&temperature_sync, &temperature_mutex);
            ////LOGE("do_temperature02");
            if(mIsTemperaturing)
                do_temperature_callback(env, HoldBuffer);
            ////LOGE("do_temperature03");
        }
        pthread_mutex_unlock(&temperature_mutex);
    }
    pthread_cond_broadcast(&temperature_sync);
    ////LOGE("do_temperature EXIT");
	EXIT();
}

void UVCPreviewIR::do_temperature_callback(JNIEnv *env, uint8_t *frameData) {
	ENTER();
	if(UNLIKELY(isNeedWriteTable))
	{
		//ic.init(requestWidth, requestHeight, (cameraLens == 68) ? 3 : 1, rangeMode);
		//ic.read_params((uint16_t *) HoldBuffer);
		ic.read_params((uint16_t *) HoldBuffer);
		Refltmp = ic.temp_reflected;
		Airtmp = ic.temp_air;
		humi = ic.humidity;

		LOGE("%f %f %f %f %f", ic.emissivity, ic.temp_reflected, ic.temp_air, ic.humidity, ic.distance);
		ic.update_table((uint16_t *) HoldBuffer);

		//LOGE("min: %f, max: %f, tc: %f   %f", min, max, tc, temperatureTable[t_min]);
		LOGE("a: %f %f %f %f %f", temperatureTable[5100], temperatureTable[5600], temperatureTable[5700], temperatureTable[10000], temperatureTable[15000]);
		LOGE("c: %f %f %f %f %f", ic.table[5100], ic.table[5600], ic.table[5700], ic.table[10000], ic.table[15000]);

		LOGE("::: %f %f :::", ic.temp(ic.temp_center), ic.temp_fpa);
		char product[17], version_fw[17], serial[17];
		ic.read_version((uint16_t *) HoldBuffer, product, serial, version_fw);
		product[16] = 0;
		serial[16] = 0;
		version_fw[16] = 0;
		LOGE("VER  %s %s %s", product, serial, version_fw);
		//LOGE("maxpos: %d %d, minpos: %d %d", mx, my, ax, ay);
		/*int d;
		float fpa, core;
		tm.GetDevData(&fpa, &core, &d, &d);
		LOGE("fpa: %f %f, core: %f %f", fpa, 20.0 - ((double) (uint16_t) fpaTmp - 7800.0) / 36.0, core, ((float)(uint16_t) coreTemper) / 10.0 -273.1);*/

		isNeedWriteTable=false;
	}


	float* temperatureData = mCbTemper;
	ic.update((uint16_t *) HoldBuffer);
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

	////LOGE("centerTmp:%.2f,maxTmp:%.2f,minTmp:%.2f,avgTmp:%.2f\n",temperatureData[0],temperatureData[3],temperatureData[6],temperatureData[9]);

	jfloatArray mNCbTemper = env->NewFloatArray(requestWidth*(requestHeight-4)+10);
	env->SetFloatArrayRegion(mNCbTemper, 0, 10+requestWidth*(requestHeight-4), mCbTemper);
	if (mTemperatureCallbackObj != NULL) {
		////LOGE("do_temperature_callback mTemperatureCallbackObj1");
		env->CallVoidMethod(mTemperatureCallbackObj, iTemperatureCallback.onReceiveTemperature, mNCbTemper);
		////LOGE("do_temperature_callback2 frameNumber:%d",frameNumber);
		env->ExceptionClear();
	}
	////LOGE("do_temperature_callback DeleteLocalRef(mNCbTemper)");
	env->DeleteLocalRef(mNCbTemper);
	//temperatureData = NULL;
    ////LOGE("do_temperature_callback EXIT();");
	EXIT();
}

//打快门更新表
void UVCPreviewIR::whenShutRefresh() {
    pthread_mutex_lock(&temperature_mutex);
    {
        isNeedWriteTable=true;
    }
    pthread_mutex_unlock(&temperature_mutex);
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
