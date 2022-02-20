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
	mPreview(NULL) {
	ENTER();

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
		SAFE_DELETE(mPreview);
		// カメラをclose
		uvc_close(mDeviceHandle);
		mDeviceHandle = NULL;
	}
	if (LIKELY(mDevice)) {
		MARK("カメラを開放");
		uvc_unref_device(mDevice);
		mDevice = NULL;
	}
	// カメラ機能フラグをクリア
	if (mUsbFs) {
		close(mFd);
		mFd = 0;
		free(mUsbFs);
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

// ズーム(abs)を設定
int UVCCamera::setZoom(int zoom) {
	ENTER();
	int ret = uvc_set_zoom_abs(mDeviceHandle, zoom);
	RETURN(ret, int);
}
