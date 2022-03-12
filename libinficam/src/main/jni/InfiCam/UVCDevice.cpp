#include "UVCDevice.h"

#include <libusb.h>
#include <libuvc.h>
#include <pthread.h>
#include <unistd.h> /* close() */
#include <cstdlib> /* NULL */

void *UVCDevice::usb_handle_events(void *arg) {
	struct timeval timeout = { .tv_sec = 0, .tv_usec = 50000 };
	UVCDevice *p = (UVCDevice *) arg;
	while (!p->usb_thread_stop) /* See disconnect for importance of the timeout. */
		libusb_handle_events_timeout(p->usb_ctx, &timeout);
	return NULL;
}

UVCDevice::~UVCDevice() {
	disconnect();
}

int UVCDevice::connect(int fd) {
	disconnect(); /* Disconnect if connected. */
	usb_fd = fd;
	usb_thread_stop = 0;
	if (libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL)) {
		disconnect();
		return 1;
	}
	if (libusb_init(&usb_ctx)) {
		disconnect();
		return 2;
	}
	if (pthread_create(&usb_thread, NULL, usb_handle_events, (void *) this)) {
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

	/* Get the first supported size. */
	const uvc_format_desc_t *format = uvc_get_format_descs(uvc_devh);
	uvc_frame_desc_t *frame = NULL;
	if (format == NULL) {
		disconnect();
		return 6;
	}
	if (format->bDescriptorSubtype != UVC_VS_FORMAT_UNCOMPRESSED) {
		disconnect();
		return 7;
	}
	frame = format->frame_descs;
	if (frame == NULL) {
		disconnect();
		return 8;
	}
	width = frame->wWidth;
	height = frame->wHeight;

	return 0;
}

void UVCDevice::disconnect() {
	stream_stop(); /* Mostly just important to set streaming = 0. */
	usb_thread_stop = 1; /* Next step closes libusb devh and wakes the thread. */
	if (uvc_ctx != NULL) { /* When no uvc_ctx, we depend on the timeout in usb_handle_events(). */
		uvc_exit(uvc_ctx); /* This also closes the libusb devh. */
		uvc_ctx = NULL;
		uvc_devh = NULL;
	}
	if (usb_thread_valid) {
		pthread_join(usb_thread, NULL); /* Thread may exist regardless of uvc_ctx. */
		usb_thread_valid = 0;
	}
	if (usb_ctx != NULL) {
		libusb_exit(usb_ctx);
		usb_ctx = NULL;
	}
	if (usb_fd >= 0)
		close(usb_fd);
	usb_fd = -1;
	width = height = 0;
}

int UVCDevice::stream_start(uvc_frame_callback_t *cb, void *user_ptr) {
	uvc_stream_ctrl_t ctrl;
	if (uvc_devh == NULL || streaming)
		return 1;
	/* 0 FPS means any. */
	if (uvc_get_stream_ctrl_format_size(uvc_devh, &ctrl, format, width, height, 0))
		return 2;
	if (uvc_start_streaming(uvc_devh, &ctrl, cb, user_ptr, 0))
		return 3;
	streaming = 1;
	return 0;
}

void UVCDevice::stream_stop() {
	if (uvc_devh == NULL || !streaming)
		return;
	uvc_stop_streaming(uvc_devh);
	streaming = 0;
}

int UVCDevice::set_zoom_abs(uint16_t val) {
	if (uvc_devh == NULL)
		return 1;
	return uvc_set_zoom_abs(uvc_devh, val);
}
