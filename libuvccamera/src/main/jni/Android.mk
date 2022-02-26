#include $(call all-subdir-makefiles)
PROJ_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(PROJ_PATH)/libusb/android/jni/Android.mk
include $(PROJ_PATH)/libuvc/android/Android.mk
include $(PROJ_PATH)/UVCCamera/Android.mk
