#include "UVCDevice.h"

#include <libusb.h>
#include <libuvc.h>
#include <pthread.h>
#include <unistd.h> /* close() */
#include <cstdlib> /* nullptr */
#include <android/log.h>
#include <cstring>
#include <cstdint> /* uint16_t */

#define LOG_TAG "libinficam"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


void *UVCDevice::usb_handle_events(void *arg) {
	struct timeval timeout = { .tv_sec = 0, .tv_usec = 50000 };
	auto *p = (UVCDevice *) arg;
	while (!p->usb_thread_stop) /* See disconnect for importance of the timeout. */
		libusb_handle_events_timeout(p->usb_ctx, &timeout);
	return nullptr;
}

UVCDevice::~UVCDevice() {
	disconnect();
}

void UVCDevice::getDeviceInfo(uint16_t * vendorId, char ** manufacturerName, char ** productName){
	uvc_device_descriptor_t *uvc_device_desc;
	uvc_device_t *uvc_dev = uvc_get_device(uvc_devh);
	if(uvc_get_device_descriptor(uvc_dev, &uvc_device_desc) != UVC_SUCCESS){
		return;
	}
	*vendorId = uvc_device_desc->idVendor;
	uvc_free_device_descriptor(uvc_device_desc);  // remember to free it

	libusb_device_handle *lh = uvc_get_libusb_handle(uvc_devh);
	struct libusb_device_descriptor d{};
	libusb_device *ld = libusb_get_device(lh);
	libusb_get_device_descriptor(ld, &d);

	unsigned char buf[256];
	if (libusb_get_string_descriptor_ascii(lh, d.iManufacturer, buf, sizeof(buf)) > 0){
		*manufacturerName = strdup((const char *)buf);
	}
	if (libusb_get_string_descriptor_ascii(lh, d.iProduct, buf, sizeof(buf)) > 0){
		*productName = strdup((const char *)buf);
	}
}

int UVCDevice::connect(int fd, int & width, int & height, bool & use_raw_logic) {
	disconnect(); /* Disconnect if connected. */
	usb_fd = fd;
	usb_thread_stop = 0;
	if (libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, nullptr)) {
		disconnect();
		return 1;
	}
	if (libusb_init(&usb_ctx)) {
		disconnect();
		return 2;
	}
	if (pthread_create(&usb_thread, nullptr, usb_handle_events, (void *) this)) {
		disconnect();
		return 3;
	}
	usb_thread_valid = 1;
	if (uvc_init(&uvc_ctx, usb_ctx)) {
		disconnect();
		return 4;
	}
	if (uvc_wrap(fd, uvc_ctx, &uvc_devh)) {
		disconnect();
		return 5;
	}


	uint16_t vendorId = 0;
	char * manufacturerName = nullptr;
	char * productName = nullptr;
	getDeviceInfo(&vendorId,&manufacturerName,&productName);

	if( (manufacturerName && strcmp(manufacturerName,"Xinfrared") == 0 ) ||
		productName && (//New generation of raw frame devices
		strstr(productName,"T2S+_V2") ||
		strstr(productName,"T2L-A4L_V2") ||
		strstr(productName,"T2L-A4L_R") ||
		strstr(productName,"T2L-A4L_A") ||
		strstr(productName,"T2L-A4L_C") ||
		strstr(productName,"T2S+_R") ||
		strstr(productName,"T2S+_A") ||
		strstr(productName,"T2S+_C") ||
		strstr(productName,"T2L-A4L_A2") ||
		strstr(productName,"T2S+_A2") )
		){
		use_raw_logic = true;
	}
	if(vendorId == 0xBDA){ //p2pro
		return 99; //TODO: Not supported right now. Fix this.
	}

	LOGI("UVC device connected: vendorId=%x manufacturerName=%s productName=%s\n",vendorId,manufacturerName?manufacturerName:"",productName?productName:"");

	if(manufacturerName){
		free(manufacturerName);
	}
	if(productName){
		free(productName);
	}



	/* Get the first supported size. */
	const uvc_format_desc_t *format_desc = uvc_get_format_descs(uvc_devh);
	uvc_frame_desc_t *frame = nullptr;
	if (format_desc == nullptr) {
		disconnect();
		return 7;
	}
	if (format_desc->bDescriptorSubtype != UVC_VS_FORMAT_UNCOMPRESSED) {
		disconnect();
		return 8;
	}

	frame = format_desc->frame_descs;

	if (frame == nullptr) {
		disconnect();
		return 9;
	}

	LOGD("UVC video resolution is %dx%d\n",frame->wWidth,frame->wHeight);

	width = frame->wWidth;
	height = frame->wHeight;

	return 0;
}

void UVCDevice::disconnect() {
	stream_stop(); /* Mostly just important to set is_streaming = 0. */
	usb_thread_stop = 1; /* Next step closes libusb devh and wakes the thread. */
	if (uvc_ctx != nullptr) { /* When no uvc_ctx, we depend on the timeout in usb_handle_events(). */
		uvc_exit(uvc_ctx); /* This also closes the libusb devh. */
		uvc_ctx = nullptr;
		uvc_devh = nullptr;
	}
	if (usb_thread_valid) {
		pthread_join(usb_thread, nullptr); /* Thread may exist regardless of uvc_ctx. */
		usb_thread_valid = 0;
	}
	if (usb_ctx != nullptr) {
		libusb_exit(usb_ctx);
		usb_ctx = nullptr;
	}
	if (usb_fd >= 0)
		close(usb_fd);
	usb_fd = -1;
}

int UVCDevice::stream_start(uvc_frame_callback_t *cb, void *user_ptr, const int width, const int height, const bool use_raw_logic) {
	uvc_stream_ctrl_t ctrl;
	if (uvc_devh == nullptr || streaming)
		return 1;
	/* 0 FPS means any. */
	if (uvc_get_stream_ctrl_format_size(uvc_devh, &ctrl, format, width, height, 0))
		return 2;
	if (uvc_start_streaming(uvc_devh, &ctrl, cb, user_ptr, 0, use_raw_logic)) //patched, raw frame devices need the uvc frame metadata to be kept.
		return 3;
	streaming = 1;
	return 0;
}

void UVCDevice::stream_stop() {
	if (uvc_devh == nullptr || !streaming)
		return;
	uvc_stop_streaming(uvc_devh);
	streaming = 0;
}

int UVCDevice::set_zoom_abs(uint16_t val) {
	if (uvc_devh == nullptr)
		return 1;
	LOGD("UVC sending zoom command => %d (hex %x)\n",val,val);
	return uvc_set_zoom_abs(uvc_devh, val);
}

libusb_device_handle * UVCDevice::get_libusb_handle() {
	return uvc_get_libusb_handle(uvc_devh);
}
