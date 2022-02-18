#/*
# * UVCCamera
# * library and sample to access to UVC web camera on non-rooted Android device
# * 
# * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
# * 
# * File name: Android.mk
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
ifeq ($(TARGET_ARCH_ABI),armeabi)
LOCAL_MODULE := thermometry
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/armeabi/libthermometry.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_MODULE := thermometry
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/armeabi-v7a/libthermometry.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_MODULE := thermometry
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/arm64-v8a/libthermometry.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),x86_64)
LOCAL_MODULE := thermometry
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/x86_64/libthermometry.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),x86)
LOCAL_MODULE := thermometry
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/x86/libthermometry.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),mips)
LOCAL_MODULE := thermometry
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/mips/libthermometry.so
include $(PREBUILT_SHARED_LIBRARY)

else
$(error Target arch ABI not supported: $(TARGET_ARCH_ABI))
endif


include $(CLEAR_VARS)
ifeq ($(TARGET_ARCH_ABI),armeabi)
LOCAL_MODULE := simplePictureProcessing
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/armeabi/libsimplePictureProcessing.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_MODULE := simplePictureProcessing
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/armeabi-v7a/libsimplePictureProcessing.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_MODULE := simplePictureProcessing
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/arm64-v8a/libsimplePictureProcessing.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),x86_64)
LOCAL_MODULE := simplePictureProcessing
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/x86_64/libsimplePictureProcessing.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),x86)
LOCAL_MODULE := simplePictureProcessing
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/x86/libsimplePictureProcessing.so
include $(PREBUILT_SHARED_LIBRARY)

else ifeq ($(TARGET_ARCH_ABI),mips)
LOCAL_MODULE := simplePictureProcessing
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := ./libs/mips/libsimplePictureProcessing.so
include $(PREBUILT_SHARED_LIBRARY)
endif

include $(CLEAR_VARS)
CFLAGS := -Werror

LOCAL_C_INCLUDES := \
		$(LOCAL_PATH)/ \
		$(LOCAL_PATH)/../ \
		$(LOCAL_PATH)/../rapidjson/include \
LOCAL_C_INCLUDES += $(LOCAL_PATH)/include

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%)
LOCAL_CFLAGS += -DANDROID_NDK
LOCAL_CFLAGS += -DLOG_NDEBUG
LOCAL_CFLAGS += -DACCESS_RAW_DESCRIPTORS
LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays -fopenmp -static-openmp

LOCAL_CXXFLAGS += -fopenmp -static-openmp

#LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -ldl
LOCAL_LDLIBS := -ldl
LOCAL_LDLIBS += -llog -lOpenSLES
LOCAL_LDLIBS += -landroid -fopenmp -static-openmp

LOCAL_SHARED_LIBRARIES += usb100 uvc thermometry simplePictureProcessing
#LOCAL_ARM_MODE := arm
#LOCAL_ALLOW_UNDEFINED_SYMBOLS := true
LOCAL_SRC_FILES := \
		_onload.cpp \
		utilbase.cpp \
		UVCCamera.cpp \
		UVCPreview.cpp \
		UVCPreviewIR.cpp \
		UVCButtonCallback.cpp \
		UVCStatusCallback.cpp \
		Parameters.cpp \
		time_cal.cpp \
		serenegiant_usb_UVCCamera.cpp

LOCAL_MODULE    := UVCCamera
include $(BUILD_SHARED_LIBRARY)
