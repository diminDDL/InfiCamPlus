#ifndef __UVCDEVICE_H__
#define __UVCDEVICE_H__

#include <libusb.h>
#include <libuvc.h>
#include <pthread.h>
#include <cstdlib> /* NULL */

/* A wrapper for libuvc and libusb because connecting to an uvc device on Android gets rather
 *   involved since we need to provide our own thread for handling libusb events, etc.
 */
class UVCDevice {
	int usb_fd = -1;
	libusb_context *usb_ctx = NULL;
	pthread_t usb_thread;
	int usb_thread_stop = 1;
	int usb_thread_valid = 0;
	int streaming = 0;

	uvc_context_t *uvc_ctx = NULL;
	uvc_device_handle_t *uvc_devh = NULL;

	static void *usb_handle_events(void *arg);

public:
	uint16_t width, height;
	uvc_frame_format format = UVC_FRAME_FORMAT_ANY;

	~UVCDevice();

	int connect(int fd); /* Closes the FD on disconnect. */
	void disconnect(); /* Opening a new connection will close the previous one if it exists. */

	/* The callback gets called from a dedicated thread (it's ok to block in the callback). */
	int stream_start(uvc_frame_callback_t *cb, void *user_ptr); /* Errors if already streaming. */
	void stream_stop(); /* Attempting to stop stream is okay even when no stream. */

	int set_zoom_abs(uint16_t val);
};

#endif /* __UVCDEVICE_H__ */
