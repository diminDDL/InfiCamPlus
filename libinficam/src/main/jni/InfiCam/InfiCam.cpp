#include "InfiCam.h"
#include "UVCDevice.h"
#include "InfiFrame.h"

#include <cstdint>
#include <pthread.h>
#include <cstdlib> /* NULL */
#include <cstring> /* memcpy() */
#include <cmath> /* isnan() */
#include <android/log.h>

#define LOG_TAG "NativeCode"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


void InfiCam::uvc_callback(uvc_frame_t *frame, void *user_ptr) {
    // This function gets called every time a new frame is ready
	InfiCam *p = (InfiCam *) user_ptr;
	if (frame->data_bytes < p->dev.width * p->dev.height * 2)
		return;

    if(p->calibrating) {
        size_t frame_size = p->dev.width * (p->dev.height - InfiCam::DATA_ROWS);    // We don't want to calibrate out the data rows

        if (p->calibration_frame == nullptr) {
            p->calibration_frame = new uint16_t[frame_size];
        }

        memcpy(p->calibration_frame, frame->data, frame_size * sizeof(uint16_t));
        // Calculate mean
        uint32_t sum = 0;
        auto* frame_data = (uint16_t*)frame->data;
        for(size_t i = 0; i < frame_size; i++) {
            sum += frame_data[i];
        }
        p->offset_value = sum / frame_size;

        // Dead pixel mask calculation
        if(p->dead_pixel_mask == nullptr) {
            p->dead_pixel_mask = new bool[frame_size];
        }

        float min = 65535.0f;
        float max = 0.0f;
        for(size_t i = 0; i < frame_size; i++) {
            if((float)frame_data[i] < min) {
                min = frame_data[i];
            }
            if((float)frame_data[i] > max) {
                max = frame_data[i];
            }
        }
        float threshold = min + (max - min) * 0.05f;    // 5% threshold
        p->dead_pixel_num = 0;
        for(size_t i = 0; i < frame_size; i++) {
            p->dead_pixel_mask[i] = (float)frame_data[i] < threshold;
            if(p->dead_pixel_mask[i]) {
                p->dead_pixel_num++;
            }
        }

        p->calibrating = false;
        p->calibrated = true;
        pthread_cond_signal(&p->calibration_cond);
        return;
    }

	pthread_mutex_lock(&p->frame_callback_mutex);

    // Store the frame into intermediary buffer
    size_t frame_size = p->dev.width * p->dev.height;
    if (p->intermediary_buffer == nullptr) {
        p->intermediary_buffer = new uint16_t[frame_size];
    }

    if (p->p2_pro) {
        memcpy(p->intermediary_buffer, (uint16_t *) frame->data + 256*192, 256*192 * sizeof(uint16_t)); // use only the half of the image with the thermal data
    } else {
        memcpy(p->intermediary_buffer, frame->data, frame_size * sizeof(uint16_t));
    }


    // Apply calibration and dead pixel correction in a single pass
    if(p->raw_sensor && p->calibrated) {
        size_t frame_size_without_data = p->dev.width * (p->dev.height - InfiCam::DATA_ROWS);
        size_t width = p->dev.width;

        for(size_t i = 0; i < frame_size_without_data; i++) {
            // First apply offset calibration
            p->intermediary_buffer[i] = p->intermediary_buffer[i] + p->offset_value - p->calibration_frame[i];

            // Then check if this is a dead pixel
            if(p->dead_pixel_mask[i] && p->dead_pixel_num > 0) {
                // Calculate row and column position
                size_t row = i / width;
                size_t col = i % width;

                uint32_t sum = 0;
                uint8_t valid_neighbors = 0;

                // Check all 8 neighboring pixels
                for(int dy = -1; dy <= 1; dy++) {
                    for(int dx = -1; dx <= 1; dx++) {
                        if(dx == 0 && dy == 0) continue;

                        int neighbor_row = static_cast<int>(row) + dy;
                        int neighbor_col = static_cast<int>(col) + dx;

                        if(neighbor_row >= 0 && neighbor_row < (p->dev.height - InfiCam::DATA_ROWS) &&
                           neighbor_col >= 0 && neighbor_col < p->dev.width) {

                            size_t neighbor_idx = neighbor_row * width + neighbor_col;

                            if(!p->dead_pixel_mask[neighbor_idx]) {
                                // Use the already calibrated value from the neighbor
                                if(neighbor_idx < i) {
                                    // If we've already processed this neighbor, use its calibrated value
                                    sum += p->intermediary_buffer[neighbor_idx];
                                } else {
                                    // If we haven't processed this neighbor yet, apply calibration to it first
                                    sum += p->intermediary_buffer[neighbor_idx] + p->offset_value - p->calibration_frame[neighbor_idx];
                                }
                                valid_neighbors++;
                            }
                        }
                    }
                }

                if(valid_neighbors > 0) {
                    p->intermediary_buffer[i] = sum / valid_neighbors;
                } else {
                    // Fallback if no valid neighbors found
                    if(i > 0) {
                        p->intermediary_buffer[i] = p->intermediary_buffer[i-1];
                    } else if(i < frame_size_without_data - 1) {
                        // For the first pixel, we need to calibrate the next pixel first
                        p->intermediary_buffer[i] = p->intermediary_buffer[i+1] + p->offset_value - p->calibration_frame[i+1];
                    }
                }
            }
        }
    }

    // Use intermediary buffer
    p->infi.read_params(p->intermediary_buffer);
    if (p->table_invalid) {
        p->infi.update_table(p->intermediary_buffer);
        p->table_invalid = 0;
    } else p->infi.update(p->intermediary_buffer);

    p->infi.temp(p->intermediary_buffer, p->frame_temp);

	/* Unlock before the callback so if it decides to call a function that locks the this callback
	 *   we don't end up in a deadlock.
	 */
	pthread_mutex_unlock(&p->frame_callback_mutex);
    p->frame_callback(p, p->frame_temp, p->intermediary_buffer, p->frame_callback_arg);
}

void InfiCam::set_float(int addr, float val) {
	uint8_t *p = (uint8_t *) &val;
	dev.set_zoom_abs((((addr + 0) & 0x7F) << 8) | p[0]);
	dev.set_zoom_abs((((addr + 1) & 0x7F) << 8) | p[1]);
	dev.set_zoom_abs((((addr + 2) & 0x7F) << 8) | p[2]);
	dev.set_zoom_abs((((addr + 3) & 0x7F) << 8) | p[3]);
}

InfiCam::~InfiCam() {
	dev.disconnect();
    delete[] calibration_frame;
    delete[] intermediary_buffer;
    delete[] frame_temp;
    delete[] dead_pixel_mask;
}

int InfiCam::connect(int fd) {
	disconnect();
	/* We initialize the mutex here because we can't use exceptions with JNI and the constructor
	 *   thus isn't able to fail.
	 */
	if (pthread_mutex_init(&frame_callback_mutex, NULL))
		return 1;
    if (pthread_cond_init(&calibration_cond, NULL)) {
        pthread_mutex_destroy(&frame_callback_mutex);
        return 1;
    }
	if (dev.connect(fd, p2_pro)) {
		pthread_mutex_destroy(&frame_callback_mutex);
		return 2;
	}
    if (infi.init(dev.width, dev.height)) {
        dev.disconnect();
        pthread_mutex_destroy(&frame_callback_mutex);
        return 3;
    }
	dev.set_zoom_abs(CMD_MODE_TEMP);
	connected = 1;
	set_range(infi.range);
	return 0;
}

void InfiCam::disconnect() {
	if (connected) {
		stream_stop();
		dev.disconnect();
		pthread_mutex_destroy(&frame_callback_mutex);
        pthread_cond_destroy(&calibration_cond);
		connected = 0;
	}
}

int InfiCam::stream_start(frame_callback_t *cb, void *user_ptr) {
	if (streaming)
		return 1;
	frame_temp = (float *) calloc(infi.width * infi.height, sizeof(float));
	if (frame_temp == NULL) {
		stream_stop();
		return 2;
	}
	frame_callback = cb;
	frame_callback_arg = user_ptr;
	table_invalid = 1;
	if (dev.stream_start(uvc_callback, this)) {
		stream_stop();
		return 3;
	}
	streaming = 1;
	return 0;
}

void InfiCam::stream_stop() {
	dev.stream_stop();
	free(frame_temp);
	frame_temp = NULL;
	streaming = 0;
}

void InfiCam::set_range(int range) {
	if (connected) {
		pthread_mutex_lock(&frame_callback_mutex);
		infi.range = range;
		dev.set_zoom_abs((range == 400) ? CMD_RANGE_400 : CMD_RANGE_120);
		pthread_mutex_unlock(&frame_callback_mutex);
	} else infi.range = range;
}

void InfiCam::set_distance_multiplier(float dm) {
	if (connected) {
		pthread_mutex_lock(&frame_callback_mutex);
		infi.distance_multiplier = dm;
		pthread_mutex_unlock(&frame_callback_mutex);
	} else infi.distance_multiplier = dm;
}

void InfiCam::set_correction(float corr) {
	if (!streaming)
		return;
    if(!raw_sensor) {
        pthread_mutex_lock(&frame_callback_mutex);
        set_float(ADDR_CORRECTION, corr);
        pthread_mutex_unlock(&frame_callback_mutex);
    }else{
        this->infi.correction = corr;
    }
}

void InfiCam::set_temp_reflected(float t_ref) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_TEMP_REFLECTED, t_ref);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_temp_air(float t_air) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_TEMP_AIR, t_air);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_humidity(float humi) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_HUMIDITY, humi);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_emissivity(float emi) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_EMISSIVITY, emi);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_distance(float dist) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_DISTANCE, dist);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist) {
	if (!streaming)
		return;
	pthread_mutex_lock(&frame_callback_mutex);
	set_float(ADDR_CORRECTION, corr);
	set_float(ADDR_TEMP_REFLECTED, t_ref);
	set_float(ADDR_TEMP_AIR, t_air);
	set_float(ADDR_HUMIDITY, humi);
	set_float(ADDR_EMISSIVITY, emi);
	set_float(ADDR_DISTANCE, dist);
	pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::store_params() {
	dev.set_zoom_abs(CMD_STORE);
}

void InfiCam::update_table() {
	table_invalid = 1;
}

void InfiCam::close_shutter() {
    if (!streaming)
        return;
    if(!infi.raw_sensor){
        calibrate();
    }else{
        dev.set_zoom_abs(CMD_SHUTTER);
    }
}

std::future<void> InfiCam::calibration_thread_future;
void InfiCam::calibration_thread() {
    dev.set_zoom_abs(CMD_SHUTTER);
    pthread_mutex_lock(&frame_callback_mutex);
    update_table();
    this->calibrating = true;

    // Wait for 500ms to let the shutter close
    struct timespec wait_time = {0, 500000000};
    nanosleep(&wait_time, NULL);

    struct timespec timeout = {0, 0};
    clock_gettime(CLOCK_REALTIME, &timeout);
    timeout.tv_sec += 2;  // 2 second timeout

    this->calibrating = true;
    int ret = 0;
    // Wait until calibration is done or timeout
    while(this->calibrating && ret == 0) {
        ret = pthread_cond_timedwait(&calibration_cond, &frame_callback_mutex, &timeout);
    }

    // If we timed out, reset the flags
    if(ret == ETIMEDOUT) {
        LOGD("Calibration timed out");
        this->calibrating = false;
        this->calibrated = false;
    } else {
        this->calibrated = true;
    }
    pthread_mutex_unlock(&frame_callback_mutex);
}

void InfiCam::calibrate() {
    if (!streaming)
        return;
    if(!(this->raw_sensor)) {
        dev.set_zoom_abs(CMD_SHUTTER);
        pthread_mutex_lock(&frame_callback_mutex);
        update_table();
        pthread_mutex_unlock(&frame_callback_mutex);
    } else {
        if(calibration_thread_future.valid()) {
            // Check if the future is actually done
            std::future_status status = calibration_thread_future.wait_for(std::chrono::seconds(0));
            if(status == std::future_status::ready) {
                // Thread is done, get the result to clear the future
                calibration_thread_future.get();
                // Start new calibration
                calibration_thread_future = std::async(std::launch::async,
                                                       &InfiCam::calibration_thread, this);
            } else {
                LOGD("Calibration thread already running");
            }
        } else {
            // Start new calibration
            calibration_thread_future = std::async(std::launch::async,
                                                   &InfiCam::calibration_thread, this);
        }
    }
}

void InfiCam::set_palette(uint32_t *palette) {
	if (streaming)
		pthread_mutex_lock(&frame_callback_mutex);
	memcpy(infi.palette, palette, palette_len * sizeof(uint32_t));
	if (streaming)
		pthread_mutex_unlock(&frame_callback_mutex);
}
