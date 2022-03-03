PROJ_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(PROJ_PATH)/libusb/android/jni/Android.mk
include $(PROJ_PATH)/libuvc_build/libuvc.mk
include $(PROJ_PATH)/InfiCam/Android.mk
