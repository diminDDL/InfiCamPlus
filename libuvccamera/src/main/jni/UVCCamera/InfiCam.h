#ifndef __INFICAM_H__
#define __INFICAM_H__

#include "libuvc.h"

class InfiCam {
    uvc_context_t *mContext;
    uvc_device_t *mDevice;
    uvc_device_handle_t *mDeviceHandle;

};

#endif /* __INFICAM_H__ */
