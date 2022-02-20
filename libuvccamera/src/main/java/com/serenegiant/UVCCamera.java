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

package com.serenegiant;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.USBMonitor.UsbControlBlock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UVCCamera {
	private static final boolean DEBUG = false;	// TODO set false when releasing
	private static final String TAG = UVCCamera.class.getSimpleName();
	private static final String DEFAULT_USBFS = "/dev/bus/usb";

	public static final int DEFAULT_PREVIEW_WIDTH = 640;
	public static final int DEFAULT_PREVIEW_HEIGHT = 480;
	public static final int DEFAULT_PREVIEW_MODE = 0;
	public static final int DEFAULT_PREVIEW_MIN_FPS = 1;
	public static final int DEFAULT_PREVIEW_MAX_FPS = 30;
	public static final float DEFAULT_BANDWIDTH = 1.0f;

	public static final int FRAME_FORMAT_YUYV = 0;
	public static final int FRAME_FORMAT_MJPEG = 1;

	public static final int PIXEL_FORMAT_RAW = 0;
	public static final int PIXEL_FORMAT_YUV = 1;
	public static final int PIXEL_FORMAT_RGB565 = 2;
	public static final int PIXEL_FORMAT_RGBX = 3;
	public static final int PIXEL_FORMAT_YUV420SP = 4;
	public static final int PIXEL_FORMAT_NV21 = 5;		// = YVU420SemiPlanar

	//--------------------------------------------------------------------------------
    public static final int CTRL_ZOOM_ABS		= 0x00000200;	// D9:  Zoom (Absolute)

    public static final int PU_BRIGHTNESS		= 0x80000001;	// D0: Brightness
    public static final int PU_CONTRAST			= 0x80000002;	// D1: Contrast

	// uvc_status_class from libuvc.h
	public static final int STATUS_CLASS_CONTROL = 0x10;
	public static final int STATUS_CLASS_CONTROL_CAMERA = 0x11;
	public static final int STATUS_CLASS_CONTROL_PROCESSING = 0x12;

	// uvc_status_attribute from libuvc.h
	public static final int STATUS_ATTRIBUTE_VALUE_CHANGE = 0x00;
	public static final int STATUS_ATTRIBUTE_INFO_CHANGE = 0x01;
	public static final int STATUS_ATTRIBUTE_FAILURE_CHANGE = 0x02;
	public static final int STATUS_ATTRIBUTE_UNKNOWN = 0xff;
	private static boolean isLoaded;
	static {
		if (!isLoaded) {
			//System.loadLibrary("jpeg-turbo1500");
			System.loadLibrary("usb100");
			System.loadLibrary("uvc");
			System.loadLibrary("UVCCamera");
			isLoaded = true;
		}
	}

	private UsbControlBlock mCtrlBlock;
    protected long mControlSupports;			// カメラコントロールでサポートしている機能フラグ
    protected long mProcSupports;				// プロセッシングユニットでサポートしている機能フラグ
    protected int mCurrentFrameFormat = FRAME_FORMAT_MJPEG;
	protected int mCurrentWidth = DEFAULT_PREVIEW_WIDTH, mCurrentHeight = DEFAULT_PREVIEW_HEIGHT;
	protected float mCurrentBandwidthFactor = DEFAULT_BANDWIDTH;
    protected String mSupportedSize;
    protected List<Size> mCurrentSizeList;

	// these fields from here are accessed from native code and do not change name and remove
    protected long mNativePtr;
    protected int mBrightnessMin, mBrightnessMax, mBrightnessDef; // TODO (netman) I am not sure we need the brightness and contrast stuff
    protected int mContrastMin, mContrastMax, mContrastDef;
    // until here

    /**
     * the sonctructor of this class should be call within the thread that has a looper
     * (UI thread or a thread that called Looper.prepare)
     */
    public UVCCamera() {
    	mNativePtr = nativeCreate();
    	mSupportedSize = null;
	}

    /**
     * connect to a UVC camera
     * USB permission is necessary before this method is called
     * @param ctrlBlock
     */
    public synchronized void open(final UsbControlBlock ctrlBlock) {
    	int result;
    	try {
			mCtrlBlock = ctrlBlock.clone();
			result = nativeConnect(mNativePtr,
				mCtrlBlock.getVenderId(), mCtrlBlock.getProductId(),
				mCtrlBlock.getFileDescriptor(),
				mCtrlBlock.getBusNum(),
				mCtrlBlock.getDevNum(),
				getUSBFSName(mCtrlBlock));
		} catch (final Exception e) {
			Log.w(TAG, e);
			result = -1;
		}
		if (result != 0) {
			throw new UnsupportedOperationException("open failed:result=" + result);
		}
    	if (mNativePtr != 0 && TextUtils.isEmpty(mSupportedSize)) {
    		mSupportedSize = nativeGetSupportedSize(mNativePtr);
		}
		nativeSetPreviewSize(mNativePtr, DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT,
			DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, DEFAULT_PREVIEW_MODE, DEFAULT_BANDWIDTH);
    }

	/**
	 * set status callback
	 *
	 */
	public void whenShutRefresh() {
		if (mNativePtr != 0) {
			nativeWhenShutRefresh(mNativePtr);
		}
	}

	public void whenChangeTempPara() {
		if (mNativePtr != 0) {
            nativeWhenShutRefresh(mNativePtr);
		}
	}

	/**
	 * onTemperatureCallback
	 * @param temp
	 */
	public void  onTemperatureCallback(float temp){
		Log.e("onTemperatureCallback:","temp="+temp);
	}

    /**
     * close and release UVC camera
     */
    public synchronized void close() {
    	//stopPreview();
    	if (mNativePtr != 0) {
    		nativeRelease(mNativePtr);
//    		mNativePtr = 0;	// nativeDestroyを呼ぶのでここでクリアしちゃダメ
    	}
    	if (mCtrlBlock != null) {
			mCtrlBlock.close();
   			mCtrlBlock = null;
		}
		mControlSupports = mProcSupports = 0;
		mCurrentFrameFormat = -1;
		mCurrentBandwidthFactor = 0;
		mSupportedSize = null;
		mCurrentSizeList = null;
    	if (DEBUG) Log.v(TAG, "close:finished");
    }

	public UsbDevice getDevice() {
		return mCtrlBlock != null ? mCtrlBlock.getDevice() : null;
	}
	public String getDeviceName(){
		return mCtrlBlock != null ? mCtrlBlock.getDeviceName() : null;
	}

	public synchronized String getSupportedSize() {
    	return !TextUtils.isEmpty(mSupportedSize) ? mSupportedSize : (mSupportedSize = nativeGetSupportedSize(mNativePtr));
    }

	public Size getPreviewSize() {
		Size result = null;
		final List<Size> list = getSupportedSizeList();
		for (final Size sz: list) {
			if ((sz.width == mCurrentWidth)
				|| (sz.height == mCurrentHeight)) {
				result =sz;
				break;
			}
		}
		return result;
	}
	
	/**
	 * Set preview size and preview mode
	 * @param width
	   @param height
	 */
	public void setPreviewSize(final int width, final int height) {
		setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, mCurrentFrameFormat, mCurrentBandwidthFactor);
	}

	/**
	 * Set preview size and preview mode
	 * @param width
	 * @param height
	 * @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
	 */
	public void setPreviewSize(final int width, final int height, final int frameFormat) {
		setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, frameFormat, mCurrentBandwidthFactor);
	}
	
	/**
	 * Set preview size and preview mode
	 * @param width
	   @param height
	   @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
	   @param bandwidth [0.0f,1.0f]
	 */
	public void setPreviewSize(final int width, final int height, final int frameFormat, final float bandwidth) {
		setPreviewSize(width, height, DEFAULT_PREVIEW_MIN_FPS, DEFAULT_PREVIEW_MAX_FPS, frameFormat, bandwidth);
	}

	/**
	 * Set preview size and preview mode
	 * @param width
	 * @param height
	 * @param min_fps
	 * @param max_fps
	 * @param frameFormat either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
	 * @param bandwidthFactor
	 */
	public void setPreviewSize(final int width, final int height, final int min_fps, final int max_fps, final int frameFormat, final float bandwidthFactor) {
		if ((width == 0) || (height == 0))
			throw new IllegalArgumentException("invalid preview size");
		if (mNativePtr != 0) {
			final int result = nativeSetPreviewSize(mNativePtr, width, height, min_fps, max_fps, frameFormat, bandwidthFactor);
			if (result != 0)
				throw new IllegalArgumentException("Failed to set preview size");
			mCurrentFrameFormat = frameFormat;
			mCurrentWidth = width;
			mCurrentHeight = height;
			mCurrentBandwidthFactor = bandwidthFactor;
		}
	}

	public List<Size> getSupportedSizeList() {
		final int type = (mCurrentFrameFormat > 0) ? 6 : 4;
		return getSupportedSize(type, mSupportedSize);
	}

	public static List<Size> getSupportedSize(final int type, final String supportedSize) {
		final List<Size> result = new ArrayList<Size>();
		if (!TextUtils.isEmpty(supportedSize))
		try {
			final JSONObject json = new JSONObject(supportedSize);
			final JSONArray formats = json.getJSONArray("formats");
			final int format_nums = formats.length();
			for (int i = 0; i < format_nums; i++) {
				final JSONObject format = formats.getJSONObject(i);
				if(format.has("type") && format.has("size")) {
					final int format_type = format.getInt("type");
					if ((format_type == type) || (type == -1)) {
						addSize(format, format_type, 0, result);
					}
				}
			}
		} catch (final JSONException e) {
			e.printStackTrace();
		}
		return result;
	}

	private static final void addSize(final JSONObject format, final int formatType, final int frameType, final List<Size> size_list) throws JSONException {
		final JSONArray size = format.getJSONArray("size");
		final int size_nums = size.length();
		for (int j = 0; j < size_nums; j++) {
			final String[] sz = size.getString(j).split("x");
			try {
				size_list.add(new Size(formatType, frameType, j, Integer.parseInt(sz[0]), Integer.parseInt(sz[1])));
			} catch (final Exception e) {
				break;
			}
		}
	}

    /**
     * set preview surface with SurfaceHolder</br>
     * you can use SurfaceHolder came from SurfaceView/GLSurfaceView
     * @param holder
     */
    public synchronized void setPreviewDisplay(final SurfaceHolder holder) {
   		nativeSetPreviewDisplay(mNativePtr, holder.getSurface());
    }

    /**
     * set preview surface with SurfaceTexture.
     * this method require API >= 14
     * @param texture
     */
    public synchronized void setPreviewTexture(final SurfaceTexture texture) {	// API >= 11
    	final Surface surface = new Surface(texture);	// XXX API >= 14
    	nativeSetPreviewDisplay(mNativePtr, surface);
    }

    /**
     * set preview surface with Surface
     * @param surface
     */
    public synchronized void setPreviewDisplay(final Surface surface) {
    	nativeSetPreviewDisplay(mNativePtr, surface);
    }

	/**
	 * para of temp
	 *
	 *
	 */
	public byte[] getByteArrayTemperaturePara(int len) {
		boolean status = false;
		return nativeGetByteArrayTemperaturePara(mNativePtr, len);
	}

	/**
	 * set ir temperature callback
	 * @param callback
	 *
	 */
	public void setTemperatureCallback(final ITemperatureCallback callback) {
		if (mNativePtr != 0) {
			nativeSetTemperatureCallback(mNativePtr, callback);
		}
	}

    /**
     * start preview
     */
    public synchronized void startPreview() {
    	if (mCtrlBlock != null) {
    		nativeStartPreview(mNativePtr);
    	}
    }

    /**
     * stop preview
     */
    public synchronized void stopPreview() {
	//	setTemperatureCallback(null); // TODO why commented?
    	if (mCtrlBlock != null) {
    		nativeStopPreview(mNativePtr);
    	}
    }

    /**
     * destroy UVCCamera object
     */
    public synchronized void destroy() {
    	close();
    	if (mNativePtr != 0) {
    		nativeDestroy(mNativePtr);
    		mNativePtr = 0;
    	}
    }

//================================================================================
    /**
     * this may not work well with some combination of camera and device
     * @param zoom [%]
     */
	public synchronized void setZoom(final int zoom) {
    	if (mNativePtr != 0) {
			nativeSetZoom(mNativePtr, zoom);
    	}
    }

	private final String getUSBFSName(final UsbControlBlock ctrlBlock) {
		String result = null;
		final String name = ctrlBlock.getDeviceName();
		final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
		if ((v != null) && (v.length > 2)) {
			final StringBuilder sb = new StringBuilder(v[0]);
			for (int i = 1; i < v.length - 2; i++)
				sb.append("/").append(v[i]);
			result = sb.toString();
		}
		if (TextUtils.isEmpty(result)) {
			Log.w(TAG, "failed to get USBFS path, try to use default path:" + name);
			result = DEFAULT_USBFS;
		}
		return result;
	}

    // #nativeCreate and #nativeDestroy are not static methods.
    private final native long nativeCreate();
    private final native void nativeDestroy(final long id_camera);
    private final native int nativeConnect(long id_camera, int venderId, int productId, int fileDescriptor, int busNum, int devAddr, String usbfs);

    private static final native int nativeRelease(final long id_camera);
	private static final native int nativeSetStatusCallback(final long mNativePtr, final IStatusCallback callback);
    private static final native int nativeSetPreviewSize(final long id_camera, final int width, final int height, final int min_fps, final int max_fps, final int mode, final float bandwidth);
    private static final native String nativeGetSupportedSize(final long id_camera);
    private static final native int nativeStartPreview(final long id_camera);
    private static final native int nativeStopPreview(final long id_camera);
    private static final native int nativeSetPreviewDisplay(final long id_camera, final Surface surface);
    private static final native int nativeGetByteArrayPicture(final long mNativePtr,byte[] frame);
	private static final native int nativeSetUserPalette(final long mNativePtr,int typeOfPalette,byte[] palette);
	private static final native byte[] nativeGetByteArrayTemperaturePara(final long mNativePtr,int len);
	private static final native int nativeSetTemperatureCallback(final long mNativePtr,  final ITemperatureCallback callback);
	private static final native void nativeWhenShutRefresh(final long mNativePtr);
	private static final native void nativeWhenChangeTempPara(final long mNativePtr);
	private static final native void nativeChangePalette(final long id_camera, int typeOfPalette);
	private static final native void nativeSetTempRange(final long id_camera, int range);
	private static final native void nativeSetShutterFix(final long id_camera, float mShutterFix);
	private static final native void nativeSetCameraLens(final long id_camera, int mCameraLens);
	private static final native int nativeStartStopTemp(final long id_camera, int startStop);

	private static final native int nativeSetZoom(final long id_camera, final int zoom);
//**********************************************************************

	public void startTemp() {
		if (mCtrlBlock != null) {
			Log.e(TAG, "startTemp");
			nativeStartStopTemp(mNativePtr, 1);
		}
	}

	public void stopTemp() {
		if (mCtrlBlock != null) {
			Log.e(TAG, "stopTemp");
			nativeStartStopTemp(mNativePtr, 0);
		}
	}

	public void  changePalette(int typeOfPalette) {
		if (mCtrlBlock != null) {
			//Log.e(TAG, "stopTemp");
			if(typeOfPalette>=6){
				int intPalette[]=new int[256*3];
				byte palette[]=new byte[256*3];
				for(int i=0;i<256*3;i++){
					palette[i]=(byte)intPalette[i];
					//palette[i]=(byte)150;
				}
				nativeSetUserPalette(mNativePtr,typeOfPalette,palette);
			}else {
				nativeChangePalette(mNativePtr, typeOfPalette);
			}
		}
	}

	public void setTempRange(int range) {
		if (mCtrlBlock != null) {
			nativeSetTempRange(mNativePtr, range);
		}
	}

	public void setShutterFix(float mShutterFix) {
		if (mCtrlBlock != null) {
			Log.e(TAG, "");
			nativeSetShutterFix(mNativePtr, mShutterFix);
		}
	}

	public void setCameraLens(int mCameraLens) {
		if (mCtrlBlock != null) {
			Log.e(TAG, "");
			nativeSetCameraLens(mNativePtr, mCameraLens);
		}
	}
}
