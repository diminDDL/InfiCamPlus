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

import java.util.List;

public class UVCCamera {
	private static final boolean DEBUG = false;	// TODO set false when releasing
	private static final String TAG = UVCCamera.class.getSimpleName();
	private static final String DEFAULT_USBFS = "/dev/bus/usb";

    public static final int CTRL_ZOOM_ABS		= 0x00000200;	// D9:  Zoom (Absolute)

	private static boolean isLoaded;
	static {
		if (!isLoaded) {
			System.loadLibrary("usb1.0");
			System.loadLibrary("uvc");
			System.loadLibrary("UVCCamera");
			isLoaded = true;
		}
	}

	private UsbControlBlock mCtrlBlock;
    protected long mControlSupports;			// カメラコントロールでサポートしている機能フラグ
    protected long mProcSupports;				// プロセッシングユニットでサポートしている機能フラグ
    protected String mSupportedSize;
    protected List<Size> mCurrentSizeList;

	// these fields from here are accessed from native code and do not change name and remove
    protected long mNativePtr;
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
    private static final native String nativeGetSupportedSize(final long id_camera);
    private static final native int nativeStartPreview(final long id_camera);
    private static final native int nativeStopPreview(final long id_camera);
    private static final native int nativeSetPreviewDisplay(final long id_camera, final Surface surface);
	private static final native int nativeSetUserPalette(final long mNativePtr,int typeOfPalette,byte[] palette);
	private static final native byte[] nativeGetByteArrayTemperaturePara(final long mNativePtr,int len);
	private static final native int nativeSetTemperatureCallback(final long mNativePtr,  final ITemperatureCallback callback);
	private static final native void nativeWhenShutRefresh(final long mNativePtr);
	private static final native void nativeChangePalette(final long id_camera, int typeOfPalette);
	private static final native void nativeSetTempRange(final long id_camera, int range);
	private static final native void nativeSetCameraLens(final long id_camera, int mCameraLens);

	private static final native int nativeSetZoom(final long id_camera, final int zoom);
//**********************************************************************

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

	public void setCameraLens(int mCameraLens) {
		if (mCtrlBlock != null) {
			Log.e(TAG, "");
			nativeSetCameraLens(mNativePtr, mCameraLens);
		}
	}
}
