#include "UVCDevice.h"

#include <libusb.h>
#include <libuvc.h>
#include <pthread.h>
#include <unistd.h> /* close() */

void *UVCDevice::usb_handle_events(void *arg) {
    UVCDevice *p = (UVCDevice *) arg;
    while (!p->usb_thread_stop)
        libusb_handle_events_completed(p->usb_ctx, &p->usb_thread_stop);
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
    if (pthread_create(&usb_thread, NULL, usb_handle_events, (void*) this)) {
        disconnect();
        return 5;
    }
    if (uvc_init(&uvc_ctx, usb_ctx)) {
        disconnect();
        return 3;
    }
    if (uvc_wrap(fd, uvc_ctx, &uvc_devh)) {
        disconnect();
        return 4;
    }
    return 0;
}

void UVCDevice::disconnect() {
    if (uvc_ctx != NULL) {
        uvc_exit(uvc_ctx); /* This also closes the devh. */
        uvc_ctx = NULL;
        uvc_devh = NULL;
    }
    usb_thread_stop = 1;
    pthread_join(usb_thread, NULL);
    if (usb_ctx != NULL) {
        libusb_exit(usb_ctx);
        usb_ctx = NULL;
    }
    close(usb_fd);
}
