#ifndef INFICAM_H_
#define INFICAM_H_

#include "CameraSettings.h"
#include "UVCDevice.h"
#include "InfiFrame.h"
#include <cstdint>
#include <cmath> /* NAN */
#include <future>
#include <pthread.h>
#include <sys/types.h>
#include <chrono>

using steady_clock = std::chrono::steady_clock;

/* This one is for actually interacting with the thermal camera, wraps UVCDevice and InfiFrame.
 *
 * Note that because Android does not allow libusb to do device discovery related stuff, there is
 *   no way to detect when the device has disconnected here, this has to be implemented separately.
 *
 * The callback given to stream_start() can block as long as it wants since libuvc has a separate
 *   thread dedicated to calling the user callback, in the worst case a few frames are missed.
 */

typedef struct {
	bool enable;
	int interval_min;
	int interval_max;
	int next_interval;
} AutoShutterData;

class InfiCam {
	static const int CMD_SHUTTER = 0x8000;
	static const int CMD_DOWNLOAD_CAL = 0x8081; //Only for raw sensors. Asks the camera to send the per-pixel calibration data.
	static const int CMD_MODE_TEMP = 0x8004;

	static const int CMD_RANGE_120 = 0x8020;
	static const int CMD_RANGE_400 = 0x8021;

	static const int CMD_STORE = 0x80FF;
	static const int ADDR_CORRECTION = 0;
	static const int ADDR_TEMP_REFLECTED = 4;
	static const int ADDR_TEMP_AIR = 8;
	static const int ADDR_HUMIDITY = 12;
	static const int ADDR_EMISSIVITY = 16;
	static const int ADDR_DISTANCE = 20;


	typedef void (frame_callback_t)(const void *inficam_jni, const float *temp, const CameraSettings& p_cam_settings);
	typedef void (settings_callback_t)(const void *inficam_jni, const CameraSettings& cam_settings);

private:
	bool exit_all_theads = false;

	//data
	const void * inficam_jni;
	UVCDevice dev;
	InfiFrame * infiframe{};
	AutoShutterData auto_shut_data{false, 0, 0, 0};
	steady_clock::time_point calibration_shutter_time; //time since start of last calibration
	steady_clock::time_point settle_start_time; //time since last upsetting event (startup, range change)
	uint16_t* packet_reconstruction_buffer = nullptr; //temporary, only used for raw sensor
	bool is_ready = false; //is camera streaming, initialized and ready to accept commands
	bool is_calibrating = false; //is in the process of calibrating

	//synchronisation
	pthread_mutex_t command_mutex{};

	//callbacks
	frame_callback_t *frame_callback{};
	settings_callback_t *settings_callback{};

	//shutter
	pthread_t shutter_thread{};
	pthread_mutex_t shutter_mutex{};
	pthread_cond_t shutter_request{};
	bool keep_shutter_closed = false;
	static void * shutter_thread_func(void *arg);


	static void uvc_frame_callback(uvc_frame_t *frame, void *user_ptr);

	static bool reset_cal_on_bad_data(InfiCam * t, const uint16_t * frame);

	void set_float(const int addr, const float val);
	void set_ushort(const int addr, const uint16_t val);

	void refresh_auto_shut_interval();

public:
	InfiCam(const void * inficam_jni);
	~InfiCam();

	//data
	CameraSettings cam_settings; //to allow JNI to read current settings

	pthread_mutex_t cal_mutex{}; //to allow JNI to wait until calibration is complete
	pthread_cond_t cal_request{};

	int connect(int uvc_fd, int & output_width, int & output_height, settings_callback_t * settings_callback);
	void disconnect();

	int stream_start(frame_callback_t *cb);
	void stream_stop(); /* Attempting to stop stream is okay even when no stream. */


	void calibrate();
	void lock_shutter();
	void unlock_shutter();

	std::vector<std::array<float,2>> get_ranges();
	void set_range(const int range);
	void set_correction(const float corr);
	void set_temp_reflected(const float t_ref);
	void set_temp_air(const float t_air);
	void set_humidity(const float humi);
	void set_emissivity(const float emi);
	void set_distance(const uint16_t dist);
	void store_params(); /* Store user memory to camera so values remain when reconnecting. */

	void set_auto_shutter_settings(bool enable, int interval_min, int interval_max);

};

#endif /* INFICAM_H_ */
