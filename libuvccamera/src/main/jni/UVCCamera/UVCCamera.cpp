/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: UVCCamera.cpp
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

#define LOG_TAG "UVCCamera"
#if 1	// デバッグ情報を出さない時1
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// LOGV/LOGD/MARKを出力しない時
		#endif
	#undef USE_LOGALL			// 指定したLOGxだけを出力
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
	#undef NDEBUG
	#define GET_RAW_DESCRIPTOR
#endif

//**********************************************************************
//
//**********************************************************************
#include <stdlib.h>
#include <linux/time.h>
#include <unistd.h>
#include <string.h>
#include "UVCCamera.h"
#include "UVCPreviewIR.h"
//#include "UVCPreviewCommon.h"
#include "Parameters.h"
#include "libuvc_internal.h"

#define	LOCAL_DEBUG 0

//**********************************************************************
//
//**********************************************************************
/**
 * コンストラクタ
 */
UVCCamera::UVCCamera()
:	mFd(0),
	mUsbFs(NULL),
	mContext(NULL),
	mDevice(NULL),
	mDeviceHandle(NULL),
	mStatusCallback(NULL),
	mPreview(NULL),
	mCtrlSupports(0),
	mPUSupports(0) {

	ENTER();
	clearCameraParams();
	EXIT();
}

/**
 * デストラクタ
 */
UVCCamera::~UVCCamera() {
	ENTER();
	release();
	if (mContext) {
		uvc_exit(mContext);
		mContext = NULL;
	}
	if (mUsbFs) {
		free(mUsbFs);
		mUsbFs = NULL;
	}
	EXIT();
}

void UVCCamera::clearCameraParams() {
	mBrightness.min = mBrightness.max = mBrightness.def = 0;
	mContrast.min = mContrast.max = mContrast.def = 0;
	mZoom.min = mZoom.max = mZoom.def = 0;
}

//======================================================================
/**
 * カメラへ接続する
 */
int UVCCamera::connect(int vid, int pid, int fd, int busnum, int devaddr, const char *usbfs) {
	ENTER();
	LOGE("UVCCamera connect vid:%d,pid:%d",vid,pid);
	uvc_error_t result = UVC_ERROR_BUSY;
	if (!mDeviceHandle && fd) {
		if (mUsbFs)
			free(mUsbFs);
		mUsbFs = strdup(usbfs);
		if (UNLIKELY(!mContext)) {
			result = uvc_init2(&mContext, NULL, mUsbFs);
//			libusb_set_debug(mContext->usb_ctx, LIBUSB_LOG_LEVEL_DEBUG);
			if (UNLIKELY(result < 0)) {
				LOGD("failed to init libuvc");
				RETURN(result, int);
			}
		}
		// カメラ機能フラグをクリア
		clearCameraParams();
		fd = dup(fd);
		// 指定したvid,idを持つデバイスを検索, 見つかれば0を返してmDeviceに見つかったデバイスをセットする(既に1回uvc_ref_deviceを呼んである)
//		result = uvc_find_device2(mContext, &mDevice, vid, pid, NULL, fd);
		result = uvc_get_device_with_fd(mContext, &mDevice, vid, pid, NULL, fd, busnum, devaddr);
		if (LIKELY(!result)) {
			// カメラのopen処理
			result = uvc_open(mDevice, &mDeviceHandle);
			if (LIKELY(!result)) {
				// open出来た時
#if LOCAL_DEBUG
				uvc_print_diag(mDeviceHandle, stderr);
#endif
				mFd = fd;
				mStatusCallback = new UVCStatusCallback(mDeviceHandle);
				mPreview = new UVCPreviewIR(mDeviceHandle);
				//mPreview = new UVCPreview(mDeviceHandle);
				/*if(vid==5396)//IR device
				{
				mPreview = new UVCPreviewIR(mDeviceHandle);
				}
				else//Common device
				{
				mPreview = new UVCPreviewCommon(mDeviceHandle);
				}*/

			} else {
				// open出来なかった時
				LOGE("could not open camera:err=%d", result);
				uvc_unref_device(mDevice);
//				SAFE_DELETE(mDevice);	// 参照カウンタが0ならuvc_unref_deviceでmDeviceがfreeされるから不要 XXX クラッシュ, 既に破棄されているのを再度破棄しようとしたからみたい
				mDevice = NULL;
				mDeviceHandle = NULL;
				close(fd);
			}
		} else {
			LOGE("could not find camera:err=%d", result);
			close(fd);
		}
	} else {
		// カメラが既にopenしている時
		LOGW("camera is already opened. you should release first");
	}
	RETURN(result, int);
}

// カメラを開放する
int UVCCamera::release() {
	ENTER();
	stopPreview();
	// カメラのclose処理
	if (LIKELY(mDeviceHandle)) {
		MARK("カメラがopenしていたら開放する");
		// ステータスコールバックオブジェクトを破棄
		//SAFE_DELETE(mStatusCallback);
		//SAFE_DELETE(mButtonCallback);
		// プレビューオブジェクトを破棄
		LOGE("UVCCamera::release() 0");
		SAFE_DELETE(mPreview);
		LOGE("UVCCamera::release() 1");
		// カメラをclose
		uvc_close(mDeviceHandle);
		LOGE("UVCCamera::release() 2");
		mDeviceHandle = NULL;
	}
	if (LIKELY(mDevice)) {
		MARK("カメラを開放");
		LOGE("UVCCamera::release() 3");
		uvc_unref_device(mDevice);
		LOGE("UVCCamera::release() 4");
		mDevice = NULL;
	}
	// カメラ機能フラグをクリア
	LOGE("UVCCamera::release() 5");
	clearCameraParams();
	LOGE("UVCCamera::release() 6");
	if (mUsbFs) {
	LOGE("UVCCamera::release() 7");
		close(mFd);
		LOGE("UVCCamera::release() 8");
		mFd = 0;
		free(mUsbFs);
		LOGE("UVCCamera::release() 9");
		mUsbFs = NULL;
	}
	RETURN(0, int);
}

int UVCCamera::setStatusCallback(JNIEnv *env, jobject status_callback_obj) {
	ENTER();
	int result = EXIT_FAILURE;
	if (mStatusCallback) {
		result = mStatusCallback->setCallback(env, status_callback_obj);
	}
	RETURN(result, int);
}

char *UVCCamera::getSupportedSize() {
	ENTER();
	if (mDeviceHandle) {
		UVCDiags params;
		RETURN(params.getSupportedSize(mDeviceHandle), char *)
	}
	RETURN(NULL, char *);
}

int UVCCamera::setPreviewSize(int width, int height, int min_fps, int max_fps, int mode, float bandwidth) {
	ENTER();
	int result = EXIT_FAILURE;
	if (mPreview) {
		result = mPreview->setPreviewSize(width, height, min_fps, max_fps, mode, bandwidth);
	}
		RETURN(result, int);
}

int UVCCamera::setPreviewDisplay(ANativeWindow *preview_window) {
	ENTER();
	int result = EXIT_FAILURE;
	if (mPreview) {
		result = mPreview->setPreviewDisplay(preview_window);
	}
	RETURN(result, int);
}

int UVCCamera::setTemperatureCallback(JNIEnv *env, jobject temperature_callback_obj) {
	ENTER();
		LOGE("setTemperatureCallback");
	int result = EXIT_FAILURE;
	if (mPreview) {
	LOGE("setTemperatureCallback  result");
		result = mPreview->setTemperatureCallback(env, temperature_callback_obj);
	}
	RETURN(result, int);
}

int UVCCamera::startPreview() {
	ENTER();

	int result = EXIT_FAILURE;
	if (mDeviceHandle) {
		return mPreview->startPreview();
	}
	RETURN(result, int);
}

int UVCCamera::getByteArrayTemperaturePara(uint8_t* para) {
	ENTER();
    LOGE("UVCCamera::getByteArrayTemperaturePara");
	int result = EXIT_FAILURE;
	if (mDeviceHandle) {
		result=	mPreview->getByteArrayTemperaturePara(para);
		//LOGE("UVCCamera::getByteArrayTemperaturePara:%d,%d,%d,%d,%d,%o",para[1],para[5],para[9],para[13],para[17],para);
	}
	RETURN(result, int);
}

void UVCCamera::whenShutRefresh() {
	ENTER();
	if (mDeviceHandle) {
		return mPreview->whenShutRefresh();
	}
	EXIT();
}

void UVCCamera::whenChangeTempPara() {
	ENTER();
	if (mDeviceHandle) {
		//return mPreview->whenChangeTempPara();
	}
	EXIT();
}

int UVCCamera::stopPreview() {
	ENTER();
	if (LIKELY(mPreview)) {
		mPreview->stopPreview();
	}
	RETURN(0, int);
}

int UVCCamera::stopTemp() {
	ENTER();
	int result = EXIT_FAILURE;
	if (mPreview) {
		result = mPreview->stopTemp();
	}
	RETURN(result, int);
}

int UVCCamera::startTemp() {
	ENTER();
	int result = EXIT_FAILURE;
	if (mPreview) {
		result = mPreview->startTemp();
	}
	RETURN(result, int);
}

void UVCCamera::changePalette(int typeOfPalette) {
	ENTER();
 	if (mPreview) {
 		mPreview->changePalette(typeOfPalette);
 	}
 	EXIT();
}

void UVCCamera::SetUserPalette(uint8_t* palette,int typeOfPalette) {
	ENTER();
	if (mPreview) {
  		mPreview->setUserPalette(palette,typeOfPalette);
	}
	EXIT();
}

void UVCCamera::setTempRange(int range) {
	ENTER();
	//LOGE("UVCCamera::setTempRange");
	if (mPreview) {
		mPreview->setTempRange(range);
	}
	EXIT();
}

void UVCCamera::setShutterFix(float mShutterFix) {
	ENTER();
	if (mPreview) {
		mPreview->setShutterFix(mShutterFix);
	}
	EXIT();
}

void UVCCamera::setCameraLens(int mCameraLens) {
	ENTER();
	if (mPreview) {
		mPreview->setCameraLens(mCameraLens);
	}
	EXIT();
}

//======================================================================
#define CTRL_BRIGHTNESS		0
#define CTRL_CONTRAST		1
#define	CTRL_SHARPNESS		2
#define CTRL_GAIN			3
#define CTRL_WHITEBLANCE	4
#define CTRL_FOCUS			5

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_i16 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		int16_t value;
		ret = get_func(devh, &value, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = value;
			LOGV("update_params:min value=%d,min=%d", value, values.min);
			ret = get_func(devh, &value, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = value;
				LOGV("update_params:max value=%d,max=%d", value, values.max);
				ret = get_func(devh, &value, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = value;
					LOGV("update_params:def value=%d,def=%d", value, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_u16 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		uint16_t value;
		ret = get_func(devh, &value, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = value;
			LOGV("update_params:min value=%d,min=%d", value, values.min);
			ret = get_func(devh, &value, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = value;
				LOGV("update_params:max value=%d,max=%d", value, values.max);
				ret = get_func(devh, &value, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = value;
					LOGV("update_params:def value=%d,def=%d", value, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_i8 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		int8_t value;
		ret = get_func(devh, &value, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = value;
			LOGV("update_params:min value=%d,min=%d", value, values.min);
			ret = get_func(devh, &value, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = value;
				LOGV("update_params:max value=%d,max=%d", value, values.max);
				ret = get_func(devh, &value, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = value;
					LOGV("update_params:def value=%d,def=%d", value, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_u8 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		uint8_t value;
		ret = get_func(devh, &value, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = value;
			LOGV("update_params:min value=%d,min=%d", value, values.min);
			ret = get_func(devh, &value, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = value;
				LOGV("update_params:max value=%d,max=%d", value, values.max);
				ret = get_func(devh, &value, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = value;
					LOGV("update_params:def value=%d,def=%d", value, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_u8u8 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		uint8_t value1, value2;
		ret = get_func(devh, &value1, &value2, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = (value1 << 8) + value2;
			LOGV("update_params:min value1=%d,value2=%d,min=%d", value1, value2, values.min);
			ret = get_func(devh, &value1, &value2, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = (value1 << 8) + value2;
				LOGV("update_params:max value1=%d,value2=%d,max=%d", value1, value2, values.max);
				ret = get_func(devh, &value1, &value2, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = (value1 << 8) + value2;
					LOGV("update_params:def value1=%d,value2=%ddef=%d", value1, value2, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_i8u8 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		int8_t value1;
		uint8_t value2;
		ret = get_func(devh, &value1, &value2, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = (value1 << 8) + value2;
			LOGV("update_params:min value1=%d,value2=%d,min=%d", value1, value2, values.min);
			ret = get_func(devh, &value1, &value2, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = (value1 << 8) + value2;
				LOGV("update_params:max value1=%d,value2=%d,max=%d", value1, value2, values.max);
				ret = get_func(devh, &value1, &value2, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = (value1 << 8) + value2;
					LOGV("update_params:def value1=%d,value2=%ddef=%d", value1, value2, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_i8u8u8 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		int8_t value1;
		uint8_t value2;
		uint8_t value3;
		ret = get_func(devh, &value1, &value2, &value3, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = (value1 << 16) + (value2 <<8) +value3;
			LOGV("update_params:min value1=%d,value2=%d,value3=%d,min=%d", value1, value2, value3, values.min);
			ret = get_func(devh, &value1, &value2, &value3, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = (value1 << 16) + (value2 <<8) +value3;
				LOGV("update_params:max value1=%d,value2=%d,value3=%d,max=%d", value1, value2, value3, values.max);
				ret = get_func(devh, &value1, &value2, &value3, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = (value1 << 16) + (value2 <<8) +value3;
					LOGV("update_params:def value1=%d,value2=%d,value3=%d,def=%d", value1, value2, value3, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_i32 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		int32_t value;
		ret = get_func(devh, &value, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = value;
			LOGV("update_params:min value=%d,min=%d", value, values.min);
			ret = get_func(devh, &value, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = value;
				LOGV("update_params:max value=%d,max=%d", value, values.max);
				ret = get_func(devh, &value, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = value;
					LOGV("update_params:def value=%d,def=%d", value, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values,
	paramget_func_u32 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if (!values.min && !values.max) {
		uint32_t value;
		ret = get_func(devh, &value, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values.min = value;
			LOGV("update_params:min value=%d,min=%d", value, values.min);
			ret = get_func(devh, &value, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values.max = value;
				LOGV("update_params:max value=%d,max=%d", value, values.max);
				ret = get_func(devh, &value, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values.def = value;
					LOGV("update_params:def value=%d,def=%d", value, values.def);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

static uvc_error_t update_ctrl_values(uvc_device_handle_t *devh, control_value_t &values1, control_value_t &values2,
	paramget_func_i32i32 get_func) {

	ENTER();

	uvc_error_t ret = UVC_SUCCESS;
	if ((!values1.min && !values1.max) ||(!values2.min && !values2.max)) {
		int32_t value1, value2;
		ret = get_func(devh, &value1, &value2, UVC_GET_MIN);
		if (LIKELY(!ret)) {
			values1.min = value1;
			values2.min = value2;
			LOGV("update_params:min value1=%d,value2=%d", value1, value2);
			ret = get_func(devh, &value1, &value2, UVC_GET_MAX);
			if (LIKELY(!ret)) {
				values1.max = value1;
				values2.max = value2;
				LOGV("update_params:max value1=%d,value2=%d", value1, value2);
				ret = get_func(devh, &value1, &value2, UVC_GET_DEF);
				if (LIKELY(!ret)) {
					values1.def = value1;
					values2.def = value2;
					LOGV("update_params:def value1=%d,value2=%d", value1, value2);
				}
			}
		}
	}
	if (UNLIKELY(ret)) {
		LOGD("update_params failed:err=%d", ret);
	}
	RETURN(ret, uvc_error_t);
}

#define UPDATE_CTRL_VALUES(VAL,FUNC) \
	ret = update_ctrl_values(mDeviceHandle, VAL, FUNC); \
	if (LIKELY(!ret)) { \
		min = VAL.min; \
		max = VAL.max; \
		def = VAL.def; \
	} else { \
		MARK("failed to UPDATE_CTRL_VALUES"); \
	} \

/**
 * カメラコントロール設定の下請け
 */
int UVCCamera::internalSetCtrlValue(control_value_t &values, int8_t value,
		paramget_func_i8 get_func, paramset_func_i8 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		value = value < values.min
			? values.min
			: (value > values.max ? values.max : value);
		set_func(mDeviceHandle, value);
	}
	RETURN(ret, int);
}

int UVCCamera::internalSetCtrlValue(control_value_t &values, uint8_t value,
		paramget_func_u8 get_func, paramset_func_u8 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		value = value < values.min
			? values.min
			: (value > values.max ? values.max : value);
		set_func(mDeviceHandle, value);
	}
	RETURN(ret, int);
}

int UVCCamera::internalSetCtrlValue(control_value_t &values, uint8_t value1, uint8_t value2,
		paramget_func_u8u8 get_func, paramset_func_u8u8 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		uint8_t v1min = (uint8_t)((values.min >> 8) & 0xff);
		uint8_t v2min = (uint8_t)(values.min & 0xff);
		uint8_t v1max = (uint8_t)((values.max >> 8) & 0xff);
		uint8_t v2max = (uint8_t)(values.max & 0xff);
		value1 = value1 < v1min
			? v1min
			: (value1 > v1max ? v1max : value1); 
		value2 = value2 < v2min
			? v2min
			: (value2 > v2max ? v2max : value2); 
		set_func(mDeviceHandle, value1, value2);
	}
	RETURN(ret, int);
}

int UVCCamera::internalSetCtrlValue(control_value_t &values, int8_t value1, uint8_t value2,
		paramget_func_i8u8 get_func, paramset_func_i8u8 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		int8_t v1min = (int8_t)((values.min >> 8) & 0xff);
		uint8_t v2min = (uint8_t)(values.min & 0xff);
		int8_t v1max = (int8_t)((values.max >> 8) & 0xff);
		uint8_t v2max = (uint8_t)(values.max & 0xff);
		value1 = value1 < v1min
			? v1min
			: (value1 > v1max ? v1max : value1); 
		value2 = value2 < v2min
			? v2min
			: (value2 > v2max ? v2max : value2); 
		set_func(mDeviceHandle, value1, value2);
	}
	RETURN(ret, int);
}

int UVCCamera::internalSetCtrlValue(control_value_t &values, int8_t value1, uint8_t value2, uint8_t value3,
		paramget_func_i8u8u8 get_func, paramset_func_i8u8u8 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		int8_t v1min = (int8_t)((values.min >> 16) & 0xff);
		uint8_t v2min = (uint8_t)((values.min >> 8) & 0xff);
		uint8_t v3min = (uint8_t)(values.min & 0xff);
		int8_t v1max = (int8_t)((values.max >> 16) & 0xff);
		uint8_t v2max = (uint8_t)((values.max >> 8) & 0xff);
		uint8_t v3max = (uint8_t)(values.max & 0xff);
		value1 = value1 < v1min
			? v1min
			: (value1 > v1max ? v1max : value1); 
		value2 = value2 < v2min
			? v2min
			: (value2 > v2max ? v2max : value2); 
		value3 = value3 < v3min
			? v3min
			: (value3 > v3max ? v3max : value3); 
		set_func(mDeviceHandle, value1, value2, value3);
	}
	RETURN(ret, int);
}

/**
 * カメラコントロール設定の下請け
 */
int UVCCamera::internalSetCtrlValue(control_value_t &values, int16_t value,
		paramget_func_i16 get_func, paramset_func_i16 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		value = value < values.min
			? values.min
			: (value > values.max ? values.max : value);
		set_func(mDeviceHandle, value);
	}
	RETURN(ret, int);
}

/**
 * カメラコントロール設定の下請け
 */
int UVCCamera::internalSetCtrlValue(control_value_t &values, uint16_t value,
		paramget_func_u16 get_func, paramset_func_u16 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		value = value < values.min
			? values.min
			: (value > values.max ? values.max : value);
		set_func(mDeviceHandle, value);
	}
	RETURN(ret, int);
}

/**
 * カメラコントロール設定の下請け
 */
int UVCCamera::internalSetCtrlValue(control_value_t &values, int32_t value,
		paramget_func_i32 get_func, paramset_func_i32 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		value = value < values.min
			? values.min
			: (value > values.max ? values.max : value);
		set_func(mDeviceHandle, value);
	}
	RETURN(ret, int);
}

/**
 * ir set zoom
 */
int UVCCamera::internalSetCtrlValue(control_value_t &values, int32_t value, paramset_func_u16 set_func) {
	int ret=set_func(mDeviceHandle, value);
	RETURN(ret, int);
}

/**
 * カメコントロール設定の下請け
 */
int UVCCamera::internalSetCtrlValue(control_value_t &values, uint32_t value,
									paramget_func_u32 get_func, paramset_func_u32 set_func) {
	int ret = update_ctrl_values(mDeviceHandle, values, get_func);
	if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
		value = value < values.min
				? values.min
				: (value > values.max ? values.max : value);
		set_func(mDeviceHandle, value);
	}
	RETURN(ret, int);
}

//======================================================================
// 明るさ
int UVCCamera::updateBrightnessLimit(int &min, int &max, int &def) {
	ENTER();
	int ret = UVC_ERROR_IO;
	if (mPUSupports & PU_BRIGHTNESS) {
		UPDATE_CTRL_VALUES(mBrightness, uvc_get_brightness);
	}
	RETURN(ret, int);
}

int UVCCamera::setBrightness(int brightness) {
	ENTER();
	int ret = UVC_ERROR_IO;
	if (mPUSupports & PU_BRIGHTNESS) {
		ret = internalSetCtrlValue(mBrightness, brightness, uvc_get_brightness, uvc_set_brightness);
	}
	RETURN(ret, int);
}

// 明るさの現在値を取得
int UVCCamera::getBrightness() {
	ENTER();
	if (mPUSupports & PU_BRIGHTNESS) {
		int ret = update_ctrl_values(mDeviceHandle, mBrightness, uvc_get_brightness);
		if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
			int16_t value;
			ret = uvc_get_brightness(mDeviceHandle, &value, UVC_GET_CUR);
			if (LIKELY(!ret))
				return value;
		}
	}
	RETURN(0, int);
}

//======================================================================
// コントラスト調整
int UVCCamera::updateContrastLimit(int &min, int &max, int &def) {
	ENTER();
	int ret = UVC_ERROR_IO;
	if (mPUSupports & PU_CONTRAST) {
		UPDATE_CTRL_VALUES(mContrast, uvc_get_contrast);
	}
	RETURN(ret, int);
}

// コントラストを設定
int UVCCamera::setContrast(uint16_t contrast) {
	ENTER();
	int ret = UVC_ERROR_IO;
	if (mPUSupports & PU_CONTRAST) {
		ret = internalSetCtrlValue(mContrast, contrast, uvc_get_contrast, uvc_set_contrast);
	}
	RETURN(ret, int);
}

// コントラストの現在値を取得
int UVCCamera::getContrast() {
	ENTER();
	if (mPUSupports & PU_CONTRAST) {
		int ret = update_ctrl_values(mDeviceHandle, mContrast, uvc_get_contrast);
		if (LIKELY(!ret)) {	// 正常に最小・最大値を取得出来た時
			uint16_t value;
			ret = uvc_get_contrast(mDeviceHandle, &value, UVC_GET_CUR);
			if (LIKELY(!ret))
				return value;
		}
	}
	RETURN(0, int);
}

// ズーム(abs)を設定
int UVCCamera::setZoom(int zoom) {
	ENTER();
	int ret = UVC_ERROR_IO;
	//if (mCtrlSupports & CTRL_ZOOM_ABS) {
		ret = internalSetCtrlValue(mZoom, zoom,uvc_set_zoom_abs);
	//}
	RETURN(ret, int);
}
