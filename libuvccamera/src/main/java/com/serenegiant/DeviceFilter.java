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

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;

public class DeviceFilter {

	private static final String TAG = "DeviceFilter";

	// USB Vendor ID (or -1 for unspecified)
	public final int mVendorId;
	// USB Product ID (or -1 for unspecified)
	public final int mProductId;
	// USB device or interface class (or -1 for unspecified)
	public final int mClass;
	// USB device subclass (or -1 for unspecified)
	public final int mSubclass;
	// USB device protocol (or -1 for unspecified)
	public final int mProtocol;
	// USB device manufacturer name string (or null for unspecified)
	public final String mManufacturerName;
	// USB device product name string (or null for unspecified)
	public final String mProductName;
	// USB device serial number string (or null for unspecified)
	public final String mSerialNumber;
	// set true if specific device(s) should exclude
	public final boolean isExclude;

	public DeviceFilter(final int vid, final int pid, final int clasz, final int subclass,
			final int protocol, final String manufacturer, final String product, final String serialNum) {
		this(vid, pid, clasz, subclass, protocol, manufacturer, product, serialNum, false);
	}

	public DeviceFilter(final int vid, final int pid, final int clasz, final int subclass,
			final int protocol, final String manufacturer, final String product, final String serialNum, final boolean isExclude) {
		mVendorId = vid;
		mProductId = pid;
		mClass = clasz;
		mSubclass = subclass;
		mProtocol = protocol;
		mManufacturerName = manufacturer;
		mProductName = product;
		mSerialNumber = serialNum;
		this.isExclude = isExclude;
/*		Log.i(TAG, String.format("vendorId=0x%04x,productId=0x%04x,class=0x%02x,subclass=0x%02x,protocol=0x%02x",
			mVendorId, mProductId, mClass, mSubclass, mProtocol)); */
	}

	public DeviceFilter(final UsbDevice device) {
		this(device, false);
	}

	public DeviceFilter(final UsbDevice device, final boolean isExclude) {
		mVendorId = device.getVendorId();
		mProductId = device.getProductId();
		mClass = device.getDeviceClass();
		mSubclass = device.getDeviceSubclass();
		mProtocol = device.getDeviceProtocol();
		mManufacturerName = null;	// device.getManufacturerName();
		mProductName = null;		// device.getProductName();
		mSerialNumber = null;		// device.getSerialNumber();
		this.isExclude = isExclude;
/*		Log.i(TAG, String.format("vendorId=0x%04x,productId=0x%04x,class=0x%02x,subclass=0x%02x,protocol=0x%02x",
			mVendorId, mProductId, mClass, mSubclass, mProtocol)); */
	}

	/**
	 * 指定したクラス・サブクラス・プロトコルがこのDeviceFilterとマッチするかどうかを返す
	 * mExcludeフラグは別途#isExcludeか自前でチェックすること
	 * @param clasz
	 * @param subclass
	 * @param protocol
	 * @return
	 */
	private boolean matches(final int clasz, final int subclass, final int protocol) {
		return ((mClass == -1 || clasz == mClass)
				&& (mSubclass == -1 || subclass == mSubclass) && (mProtocol == -1 || protocol == mProtocol));
	}

	/**
	 * 指定したUsbDeviceがこのDeviceFilterにマッチするかどうかを返す
	 * mExcludeフラグは別途#isExcludeか自前でチェックすること
	 * @param device
	 * @return
	 */
	public boolean matches(final UsbDevice device) {
		if (mVendorId != -1 && device.getVendorId() != mVendorId) {
			return false;
		}
		if (mProductId != -1 && device.getProductId() != mProductId) {
			return false;
		}
/*		if (mManufacturerName != null && device.getManufacturerName() == null)
			return false;
		if (mProductName != null && device.getProductName() == null)
			return false;
		if (mSerialNumber != null && device.getSerialNumber() == null)
			return false;
		if (mManufacturerName != null && device.getManufacturerName() != null
				&& !mManufacturerName.equals(device.getManufacturerName()))
			return false;
		if (mProductName != null && device.getProductName() != null
				&& !mProductName.equals(device.getProductName()))
			return false;
		if (mSerialNumber != null && device.getSerialNumber() != null
				&& !mSerialNumber.equals(device.getSerialNumber()))
			return false; */

		// check device class/subclass/protocol
		if (matches(device.getDeviceClass(), device.getDeviceSubclass(), device.getDeviceProtocol())) {
			return true;
		}

		// if device doesn't match, check the interfaces
		final int count = device.getInterfaceCount();
		for (int i = 0; i < count; i++) {
			final UsbInterface intf = device.getInterface(i);
			if (matches(intf.getInterfaceClass(), intf.getInterfaceSubclass(), intf.getInterfaceProtocol())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * このDeviceFilterに一致してかつmExcludeがtrueならtrueを返す
	 * @param device
	 * @return
	 */
	public boolean isExclude(final UsbDevice device) {
		return isExclude && matches(device);
	}

	@Override
	public int hashCode() {
		return (((mVendorId << 16) | mProductId) ^ ((mClass << 16)
				| (mSubclass << 8) | mProtocol));
	}
}
