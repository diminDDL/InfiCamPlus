LOCAL_PATH	:= $(call my-dir)

include $(CLEAR_VARS)
CFLAGS := -Werror

LOCAL_C_INCLUDES :=$(LOCAL_PATH)/../libusb/libusb $(LOCAL_PATH)/../libuvc_build

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%)
LOCAL_CFLAGS += -DANDROID_NDK
LOCAL_CFLAGS += -DACCESS_RAW_DESCRIPTORS

# Debug vs Release
ifeq ($(APP_OPTIM),debug)
    LOCAL_CFLAGS += -O0 -g -fno-inline -fno-omit-frame-pointer
else
    LOCAL_CFLAGS += -O3 -fstrict-aliasing -fprefetch-loop-arrays
    LOCAL_CFLAGS += -DLOG_NDEBUG
endif

LOCAL_LDLIBS := -ldl -llog -landroid

LOCAL_SHARED_LIBRARIES += usb1.0 uvc
LOCAL_SRC_FILES := \
		UVCDevice.cpp \
		InfiFrame.cpp \
		InfiCam.cpp \
		InfiCamJNI.cpp

LOCAL_MODULE := InfiCam
include $(BUILD_SHARED_LIBRARY)
