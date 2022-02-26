#ifndef __UVCDEVICE_H__
#define __UVCDEVICE_H__

#include <libusb.h>
#include <libuvc.h>
#include <pthread.h>
#include <cstdlib> /* NULL */

class UVCDevice {
    int usb_fd = 0;
    libusb_context *usb_ctx = NULL;
    pthread_t usb_thread;
    int usb_thread_stop = 1;

    uvc_context_t *uvc_ctx = NULL;
    uvc_device_handle_t *uvc_devh = NULL;

    static void *usb_handle_events(void *arg);

public:
    ~UVCDevice();

    int connect(int fd);
    void disconnect();
};

#endif /* __UVCDEVICE_H__ */
