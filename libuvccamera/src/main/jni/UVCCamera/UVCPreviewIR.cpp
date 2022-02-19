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
#include <stdlib.h>
#include <linux/time.h>
#include <unistd.h>
#include <math.h>
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

#include "utilbase.h"
#include "UVCPreviewIR.h"
#include "libuvc_internal.h"


#define	LOCAL_DEBUG 0
#define PREVIEW_PIXEL_BYTES 4	// RGBA/RGBX
#define OUTPUTMODE 4
//#define OUTPUTMODE 5

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
	frameBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * 2),	// YUYV
	frameMode(0),
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
    correction=0;
    Refltmp=0;
    Airtmp=0;
    humi=0;
    emiss=0;
    distance=0;
    cameraLens=130;//130;//镜头大小:目前支持两种，68：使用6.8mm镜头，130：使用13mm镜头,默认130。
    shutterFix=0;
    shutTemper=0;
    coreTemper=0;
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

inline const bool UVCPreviewIR::isRunning() const {return mIsRunning; }
inline const bool UVCPreviewIR::isComputed() const {return mIsComputed; }

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
    if(OutBuffer!=NULL){
        delete[] OutBuffer;
    }
    if(HoldBuffer!=NULL){
        delete[] HoldBuffer;
    }
    if(RgbaOutBuffer!=NULL){
        delete[] RgbaOutBuffer;
    }
    if(RgbaHoldBuffer!=NULL){
        delete[] RgbaHoldBuffer;
    }
    //释放专业图像算法占用的资源
    if(irBuffers[0].destBuffer!=NULL)
    {
        free(irBuffers[0].destBuffer);
        irBuffers[0].destBuffer=NULL;
    }
    if(irBuffers!=NULL)
    {
        free(irBuffers);
        irBuffers=NULL;
    }
    //end -释放专业图像算法占用的资源
	RETURN(0, int);
}

void UVCPreviewIR::uvc_preview_frame_callback(uint8_t *frameData, void *vptr_args,size_t hold_bytes)
{
    //LOGE("uvc_preview_frame_callback00");
    UVCPreviewIR *preview = reinterpret_cast<UVCPreviewIR *>(vptr_args);
    unsigned short* tmp_buf=(unsigned short*)frameData;
    ////LOGE("uvc_preview_frame_callback00  tmp_buf:%d,%d,%d,%d",tmp_buf[384*144*4],tmp_buf[384*144*4+1],tmp_buf[384*144*4+2],tmp_buf[384*144*4+3]);

    ////LOGE("uvc_preview_frame_callback hold_bytes:%d,preview->frameBytes:%d",hold_bytes,preview->frameBytes);

    if(LIKELY( preview->isRunning()) && hold_bytes >= preview->frameBytes)
    {
    //LOGE("uvc_preview_frame_callback01");
    memcpy(preview->OutBuffer,frameData,(preview->requestWidth)*(preview->requestHeight)*2);
    //LOGE("uvc_preview_frame_callback02");
    /* swap the buffers org */
    uint8_t* tmp_buf=NULL;
    tmp_buf =preview->OutBuffer;
    preview->OutBuffer=preview->HoldBuffer;
    preview->HoldBuffer=tmp_buf;
    tmp_buf=NULL;
    preview->signal_receive_frame_data();
    }
    //LOGE("uvc_preview_frame_callback03");
}

void UVCPreviewIR::signal_receive_frame_data()
{
    pthread_cond_signal(&preview_sync);
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
    OutBuffer=new unsigned char[requestWidth*(requestHeight)*2];
    HoldBuffer=new unsigned char[requestWidth*(requestHeight)*2];
    RgbaOutBuffer=new unsigned char[requestWidth*(requestHeight-4)*4];
    RgbaHoldBuffer=new unsigned char[requestWidth*(requestHeight-4)*4];

	for (int i = 0; i + 3 <= sizeof(paletteIronRainbow); i += 3) {
		double x = (double) i / (double) sizeof(paletteIronRainbow);
		paletteIronRainbow[i + 0] = round(255 * sqrt(x));
		paletteIronRainbow[i + 1] = round(255 * pow(x, 3));
		paletteIronRainbow[i + 2] = round(255 * fmax(0, sin(2 * M_PI * x)));
	}

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
		frameMode = requestMode;
		frameBytes = frameWidth * frameHeight * (!requestMode ? 2 : 4);
	}
	else
	 {
		////LOGE("could not negotiate with camera:err=%d", result);
	 }
	RETURN(result, int);
}

void UVCPreviewIR::do_preview(uvc_stream_ctrl_t *ctrl) {
	ENTER();
    //////LOGE("do_preview");
	uvc_error_t result = uvc_start_streaming_bandwidth(mDeviceHandle, ctrl, uvc_preview_frame_callback, (void *)this, requestBandwidth, 0);
	if (LIKELY(!result))
	{
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
                uint8_t *tmp_buf=NULL;
              //if(OutPixelFormat==3)//RGBA 32bit输出
              //  {
                    mIsComputed=false;
                    // swap the buffers rgba
                    tmp_buf =RgbaOutBuffer;
                    RgbaOutBuffer= RgbaHoldBuffer;
                    RgbaHoldBuffer=tmp_buf;
                    unsigned short* orgData=(unsigned short*)HoldBuffer;
                    unsigned short* fourLinePara=orgData+requestWidth*(requestHeight-4);//后四行参数
                        int amountPixels=0;
                        switch (requestWidth)
                        {
                            case 384:
                                amountPixels=requestWidth*(4-1);
                                break;
                            case 240:
                                amountPixels=requestWidth*(4-3);
                                break;
                            case 256:
                                amountPixels=requestWidth*(4-3);
                                break;
                            case 640:
                                amountPixels=requestWidth*(4-1);
                                break;
                        }
                    ////LOGE("cpyPara  amountPixels:%d ",amountPixels);
                    memcpy(&shutTemper,fourLinePara+amountPixels+1,sizeof(unsigned short));
                    ////LOGE("cpyPara  shutTemper:%d ",shutTemper);
                    memcpy(&coreTemper,fourLinePara+amountPixels+2,sizeof(unsigned short));//外壳
                   // //LOGE("cpyPara  coreTemper:%d ",coreTemper);
                    ////LOGE("cpyPara  floatShutTemper:%f,floatCoreTemper:%f,floatFpaTmp:%f\n",floatShutTemper,floatCoreTemper,floatFpaTmp);
                    memcpy((uint8_t*)cameraSoftVersion,fourLinePara+amountPixels+24,16*sizeof(uint8_t));//camera soft version
                    //LOGE("cameraSoftVersion:%s\n",cameraSoftVersion);
                    memcpy((uint8_t*)sn,fourLinePara+amountPixels+32,32*sizeof(uint8_t));//SN
                    //LOGE("sn:%s\n",sn);
                    int userArea=amountPixels+127;
                    memcpy(&correction,fourLinePara+userArea,sizeof( float));//修正
                    userArea=userArea+2;
                    memcpy(&Refltmp,fourLinePara+userArea,sizeof( float));//反射温度
                    userArea=userArea+2;
                    memcpy(&Airtmp,fourLinePara+userArea,sizeof( float));//环境温度
                    userArea=userArea+2;
                    memcpy(&humi,fourLinePara+userArea,sizeof( float));//湿度
                    userArea=userArea+2;
                    memcpy(&emiss,fourLinePara+userArea,sizeof( float));//发射率
                    userArea=userArea+2;
                    memcpy(&distance,fourLinePara+userArea,sizeof(unsigned short));//距离
                    //LOGE("cpyPara  distance:%d ",distance);
                    amountPixels=requestWidth*(requestHeight-4);
                    detectAvg=orgData[amountPixels];
                    amountPixels++;
                    fpaTmp=orgData[amountPixels];
                    amountPixels++;
                    maxx1=orgData[amountPixels];
                    amountPixels++;
                    maxy1=orgData[amountPixels];
                    amountPixels++;
				t_max=orgData[amountPixels];
                    //printf("cpyPara  max:%d ",max);
                    amountPixels++;
                    minx1=orgData[amountPixels];
                    amountPixels++;
                    miny1=orgData[amountPixels];
                    amountPixels++;
				t_min=orgData[amountPixels];
                    amountPixels++;
				t_avg=orgData[amountPixels];
                    //LOGE("waitPreviewFrame04");
                    draw_preview_one(HoldBuffer, &mPreviewWindow, NULL, 4);
                    tmp_buf=NULL;
                    mIsComputed=true;
               // }
            }
            pthread_mutex_unlock(&preview_mutex);
            if(mTemperatureCallbackObj&&mIsTemperaturing)
            {
                ////LOGE("do_preview1");
                pthread_cond_signal(&temperature_sync);
            }
            ////LOGE("do_preview4");

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

static void copyFrame(const uint8_t *src, uint8_t *dest, const int width, int height, const int stride_src, const int stride_dest) {
	////LOGE("copyFrame width%d,height%d,stride_src%d,stride_dest%d",width,height,stride_src,stride_dest);
	//memset(src,0,384*292*4);

	//const int h8 = height % 8;l
	for (int i = 0; i < height; i++) {
		memcpy(dest, src, width);
		dest += stride_dest;
		src += stride_src;
	}
/*memcpy(dest, src, width*height*sizeof(uint8_t));*/

	////LOGE("copyFrame2");
}

// transfer specific frame data to the Surface(ANativeWindow)
int UVCPreviewIR::copyToSurface(uint8_t *frameData, ANativeWindow **window) {
//LOGE("copyToSurface");
	// ENTER();
	int result = 0;
	if (LIKELY(*window)) {
		ANativeWindow_Buffer buffer;
		if (LIKELY(ANativeWindow_lock(*window, &buffer, NULL) == 0)) {
			// source = frame data
			const uint8_t *src = frameData;
			const int src_w = requestWidth * PREVIEW_PIXEL_BYTES;
			const int src_step = src_w;
			// destination = Surface(ANativeWindow)
			uint8_t *dest = (uint8_t *)buffer.bits;
			const int dest_w = buffer.width * PREVIEW_PIXEL_BYTES;
			const int dest_step = buffer.stride * PREVIEW_PIXEL_BYTES;
			// use lower transfer bytes
			const int w = src_w < dest_w ? src_w : dest_w;
			// use lower height
			const int h = frameHeight < buffer.height ? frameHeight : buffer.height;
			////LOGE("copyToSurface");
			// transfer from frame data to the Surface
			////LOGE("copyToSurface:w:%d,h,%d",w,h);
			copyFrame(src, dest, w, h, src_step, dest_step);
			src=NULL;
			dest=NULL;
			////LOGE("copyToSurface2");
			ANativeWindow_unlockAndPost(*window);
			//LOGE("copyToSurface3");

		} else {
        //LOGE("copyToSurface4");
			result = -1;
		}
	} else {
	//LOGE("copyToSurface5");
		result = -1;
	}
	//LOGE("copyToSurface6");
	return result; //RETURN(result, int);
}

// changed to return original frame instead of returning converted frame even if convert_func is not null.
void UVCPreviewIR::draw_preview_one(uint8_t *frameData, ANativeWindow **window, convFunc_t convert_func, int pixcelBytes) {
	unsigned short *tmp_buf = (unsigned short*) frameData;
	//8005模式下yuyv转rgba
	//uvc_yuyv2rgbx2(tmp_buf, RgbaHoldBuffer,requestWidth,requestHeight);

	/**
	 * 线性图像算法
	 * 图像效果不及专业级算法，但是处理效率快，对主频几乎没要求
	 *
	 */
	int avgSubMin= (t_avg - t_min) > 0 ? (t_avg - t_min) : 1;
	int maxSubAvg= (t_max - t_avg) > 0 ? (t_max - t_avg) : 1;
	int ro1= (t_avg - t_min) > 97 ? 97 : (t_avg - t_min);
	int ro2= (t_max - t_avg) > 157 ? 157 : (t_max - t_avg);
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
				RgbaHoldBuffer[4 * i + 0] = (unsigned char) paletteIronRainbow[paletteNum + 0];
				RgbaHoldBuffer[4 * i + 1] = (unsigned char) paletteIronRainbow[paletteNum + 1];
				RgbaHoldBuffer[4 * i + 2] = (unsigned char) paletteIronRainbow[paletteNum + 2];
				RgbaHoldBuffer[4 * i + 3] = 1;
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
	 //LOGE("not myOpencl");

	tmp_buf=NULL;

	if (LIKELY(*window))
	{
		copyToSurface(RgbaHoldBuffer, window);
	}
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
    uint8_t* TempPara;
    switch (requestWidth)
    {
        case 384:
        TempPara=HoldBuffer+(requestWidth*(requestHeight-1)+127)*2;
        break;
        case 240:
        TempPara=HoldBuffer+(requestWidth*(requestHeight-3)+127)*2;
        break;
        case 256:
        TempPara=HoldBuffer+(requestWidth*(requestHeight-3)+127)*2;
        break;
        case 640:
        TempPara=HoldBuffer+(requestWidth*(requestHeight-1)+127)*2;
        break;
    }
        memcpy(para, TempPara, 128*sizeof(uint8_t));
        TempPara=TempPara-127*2+24*2;//version
        memcpy(para+128-16, TempPara, 16*sizeof(uint8_t));
        for(int j=0;j<16;j++){
        //////LOGE("getByteArrayTemperaturePara version:%c",TempPara[j]);
        }
      //  //////LOGE("getByteArrayTemperaturePara:%d,%d,%d,%d,%d,%d",para[16],para[17],para[18],para[19],para[20],para[21]);
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
        JavaVM *vm = getVM();
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
            {
                do_temperature_callback(env, HoldBuffer);
            }
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
    unsigned short* orgData=(unsigned short *)HoldBuffer;
    unsigned short* fourLinePara=orgData+requestWidth*(requestHeight-4);//后四行参数
	if(UNLIKELY(isNeedWriteTable))
	{

		thermometryT4Line(requestWidth,
						  requestHeight,
						  temperatureTable,
						  fourLinePara,
						  &floatFpaTmp,
						  &correction,
						  &Refltmp,
						  &Airtmp,
						  &humi,
						  &emiss,
						  &distance,
						  cameraLens,
						  shutterFix,
						  rangeMode);
		isNeedWriteTable=false;
	}
		/*temperatureData[0]=centerTmp;
		temperatureData[1]=(float)maxx1;
		temperatureData[2]=(float)maxy1;
		temperatureData[3]=maxTmp;
		temperatureData[4]=(float)minx1;
		temperatureData[5]=(float)miny1;
		temperatureData[6]=minTmp;
		temperatureData[7]=point1Tmp;
		temperatureData[8]=point2Tmp;
		temperatureData[9]=point3Tmp;*/

		float* temperatureData=mCbTemper;
		//根据8004或者8005模式来查表，8005模式下仅输出以上注释的10个参数，8004模式下数据以上参数+全局温度数据
		thermometrySearch(requestWidth,requestHeight,temperatureTable,orgData,temperatureData,rangeMode,OUTPUTMODE);
		////LOGE("centerTmp:%.2f,maxTmp:%.2f,minTmp:%.2f,avgTmp:%.2f\n",temperatureData[0],temperatureData[3],temperatureData[6],temperatureData[9]);

		//temperatureData[7]=floatFpaTmp;
		//temperatureData[8]=floatShutTemper;
		//memcpy(&temperatureData[7],&RgbaHoldBuffer[(maxy1*384+maxx1)*4],4);
		//LOGE("RgbaHoldBuffer71:%d",RgbaHoldBuffer[(maxy1*384+maxx1)*4]);
		//LOGE("RgbaHoldBuffer72:%d",RgbaHoldBuffer[(maxy1*384+maxx1)*4+1]);
		//LOGE("RgbaHoldBuffer73:%d",RgbaHoldBuffer[(maxy1*384+maxx1)*4+2]);
		//LOGE("RgbaHoldBuffer74:%d",RgbaHoldBuffer[(maxy1*384+maxx1)*4+3]);
		//memcpy(&temperatureData[8],&RgbaHoldBuffer[(miny1*384+minx1)*4],4);
	jfloatArray mNCbTemper = env->NewFloatArray(requestWidth*(requestHeight-4)+10);
	env->SetFloatArrayRegion(mNCbTemper, 0, 10+requestWidth*(requestHeight-4), mCbTemper);
	if (mTemperatureCallbackObj!=NULL) {
		////LOGE("do_temperature_callback mTemperatureCallbackObj1");
		env->CallVoidMethod(mTemperatureCallbackObj, iTemperatureCallback.onReceiveTemperature, mNCbTemper);
		////LOGE("do_temperature_callback2 frameNumber:%d",frameNumber);
		env->ExceptionClear();
	}
	////LOGE("do_temperature_callback DeleteLocalRef(mNCbTemper)");
	env->DeleteLocalRef(mNCbTemper);
	temperatureData = NULL;
	orgData = NULL;
	fourLinePara = NULL;
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

void UVCPreviewIR::setUserPalette(uint8_t* palette,int typeOfPalette) {
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
    rangeMode = range;
    EXIT();
}

void UVCPreviewIR::setShutterFix(float mShutterFix) {
    ENTER();
    shutterFix = mShutterFix;
    EXIT();
}

void UVCPreviewIR::setCameraLens(int mCameraLens) {
    ENTER();
    cameraLens = mCameraLens;
    //LOGE("setCameraLens:%d\n",cameraLens);
    EXIT();
}
