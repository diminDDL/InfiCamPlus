/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.ntmn.usbcameracommon;

import android.app.Activity;

import com.ntmn.ITemperatureCallback;
import com.ntmn.widget.UVCCameraTextureView;

public class UVCCameraHandler extends AbstractUVCCameraHandler {
	private static UVCCameraHandler mUVCCameraHandler;

	/**
	 * create UVCCameraHandler, default bandwidth
	 * @param parent
	 * @param cameraView
	 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
	 * @param width
	 * @param height
	 * @param format either UVCCamera.FRAME_FORMAT_YUYV(0) or UVCCamera.FRAME_FORMAT_MJPEG(1)
	 * @return
	 */
	public static final UVCCameraHandler createHandler(
            final Activity parent, final UVCCameraTextureView cameraView,
            final int encoderType, final int width, final int height, final int format, ITemperatureCallback temperatureCallback) {

		return createHandler(parent, cameraView, encoderType, width, height, format, 1.0f, temperatureCallback);
	}

	/**
	 * create UVCCameraHandler
	 * @param parent
	 * @param cameraView
	 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
	 * @param width
	 * @param height
	 * @param format either UVCCamera.FRAME_FORMAT_YUYV(0) or UVCCamera.FRAME_FORMAT_MJPEG(1)
	 * @param bandwidthFactor
	 * @return
	 */
	public static final UVCCameraHandler
	createHandler(
			final Activity parent, final UVCCameraTextureView cameraView,
			final int encoderType, final int width, final int height, final int format, final float bandwidthFactor,ITemperatureCallback temperatureCallback) {

		final CameraThread thread = new CameraThread(UVCCameraHandler.class, parent, cameraView, encoderType, width, height, format, bandwidthFactor,temperatureCallback);
		thread.start();
		mUVCCameraHandler=(UVCCameraHandler)thread.getHandler();
		return mUVCCameraHandler;
		//return (UVCCameraHandler)thread.getHandler();
	}
	public static synchronized  UVCCameraHandler getInstance(){
		return mUVCCameraHandler;
	}
	protected UVCCameraHandler(final CameraThread thread) {
		super(thread);
	}

	@Override
	public void startPreview(final Object surface) {
		super.startPreview(surface);
	}

	@Override
	public void captureStill() {
		super.captureStill();
	}

	@Override
	public void captureStill(final String path) {
		super.captureStill(path);
	}
}
