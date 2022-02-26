/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: Parameters.cpp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/
#define LOG_TAG "Parameters"

#include "Parameters.h"
#include "libuvc/libuvc_internal.h"

UVCDiags::UVCDiags() {}
UVCDiags::~UVCDiags() {};

char *UVCDiags::getSupportedSize(const uvc_device_handle_t *deviceHandle) {
	char buf[256] = { 0 };
	if (deviceHandle->info->stream_ifs) {
		uvc_streaming_interface_t *stream_if;
		int stream_idx = 0;

		//DL_FOREACH(deviceHandle->info->stream_ifs, stream_if)
		for (stream_if = deviceHandle->info->stream_ifs; stream_if; stream_if = stream_if->next) {
			++stream_idx;
			uvc_format_desc_t *fmt_desc;
			uvc_frame_desc_t *frame_desc;
			//DL_FOREACH(stream_if->format_descs, fmt_desc)
			for (fmt_desc = stream_if->format_descs; fmt_desc; fmt_desc = fmt_desc->next) {
				int def_frame = fmt_desc->bDefaultFrameIndex;
				if (fmt_desc->bDescriptorSubtype != UVC_VS_FORMAT_UNCOMPRESSED)
					continue;
				//write(writer, "index", fmt_desc->bFormatIndex);
				for (frame_desc = fmt_desc->frame_descs; frame_desc; frame_desc = frame_desc->next) {
					snprintf(buf, sizeof(buf), "%dx%d", frame_desc->wWidth, frame_desc->wHeight);
					buf[sizeof(buf)-1] = '\0';
				}
			}
		}
	}
	RETURN(strdup(buf), char *);
}
