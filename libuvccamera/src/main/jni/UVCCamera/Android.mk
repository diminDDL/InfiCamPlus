#/*
# * UVCCamera
# * library and sample to access to UVC web camera on non-rooted Android device
# * 
# * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
# * 
# * File name: libuvc.mk
# * 
# * Licensed under the Apache License, Version 2.0 (the "License");
# * you may not use this file except in compliance with the License.
# *  You may obtain a copy of the License at
# * 
# *     http://www.apache.org/licenses/LICENSE-2.0
# * 
# *  Unless required by applicable law or agreed to in writing, software
# *  distributed under the License is distributed on an "AS IS" BASIS,
# *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# *  See the License for the specific language governing permissions and
# *  limitations under the License.
# * 
# * All files in the folder are under this Apache License, Version 2.0.
# * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
#*/

######################################################################
# Make shared library libUVCCamera.so
######################################################################
LOCAL_PATH	:= $(call my-dir)

######################################################################
# Make shared library libUVCCamera.so
######################################################################

include $(CLEAR_VARS)
CFLAGS := -Werror

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../libusb/libusb \
	$(LOCAL_PATH)/../libuvc_build

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%)
LOCAL_CFLAGS += -DANDROID_NDK
LOCAL_CFLAGS += -DLOG_NDEBUG
LOCAL_CFLAGS += -DACCESS_RAW_DESCRIPTORS
LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays

#LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -ldl
LOCAL_LDLIBS := -ldl
LOCAL_LDLIBS += -llog -lOpenSLES
LOCAL_LDLIBS += -landroid

LOCAL_SHARED_LIBRARIES += usb1.0 uvc
#LOCAL_ARM_MODE := arm
#LOCAL_ALLOW_UNDEFINED_SYMBOLS := true
LOCAL_SRC_FILES := \
		UVCPreviewIR.cpp \
		UVCDevice.cpp \
		InfiFrame.cpp \
		InfiCam.cpp \
		InfiCamJNI.cpp \
		serenegiant_usb_UVCCamera.cpp

LOCAL_MODULE    := UVCCamera
include $(BUILD_SHARED_LIBRARY)
