#include "InfiCam.h"
#include "CameraSettings.h"
#include "InfiFrame.h"
#include "Utils.h"

#include <cstdint>
#include <pthread.h>
#include <cstring> /* memcpy() */
#include <android/log.h>
#include <sys/types.h>
#include <algorithm>

static constexpr int RAW_RANGE_AUTO_SHUTTER_SETTLE_MS = 20000;
static constexpr int RAW_RANGE_VALIDATION_SHUTTER_SETTLE_MS = 300;
static constexpr int RAW_RANGE_VALIDATION_OBSERVE_MS = 1000;
static constexpr int RAW_RANGE_VALIDATION_RETRY_SPACING_MS = 1000;
static constexpr int RAW_RANGE_VALIDATION_MAX_RETRIES = 3;
static constexpr float RAW_RANGE_MIN_VALID_VARIANCE = 0.001f;
static constexpr float RAW_RANGE_VARIANCE_DELTA_ABS = 0.025f; // 0.05C stddev squared delta
static constexpr float RAW_RANGE_VARIANCE_DELTA_REL = 0.025f;

static float temperature_variance(const float *temp, const int count, int &valid_count){
	double mean = 0.0;
	double m2 = 0.0;
	valid_count = 0;
	for(int i = 0 ; i < count ; i++){
		if(!std::isfinite(temp[i])){
			continue;
		}
		valid_count++;
		double delta = (double)temp[i] - mean;
		mean += delta / (double)valid_count;
		double delta2 = (double)temp[i] - mean;
		m2 += delta * delta2;
	}
	if(valid_count <= 1){
		return 0.0f;
	}
	return (float)(m2 / (double)valid_count);
}


/*
 * DATA OWNERSHIP:
 *
 * Raw sensors:
 *	InfiCam owns the data. Commands to change data trigger the "settings_callback" immediately.
 *
 * Non-raw sensors:
 *	Camera owns the data. InfiCam has a snapshot of the data updated when a new frame comes.
 *	If the new frame shows the settings have changed, we trigger the "settings_callback".
 *
 */


InfiCam::InfiCam(const void * p_inficam_jni): inficam_jni(p_inficam_jni){
	if(pthread_mutex_init(&shutter_mutex, nullptr) ||
	   pthread_cond_init(&shutter_request, nullptr) ||
	   pthread_create(&shutter_thread, nullptr, shutter_thread_func, (void *) this) ||
	   pthread_mutex_init(&command_mutex, nullptr) ||
		pthread_mutex_init(&cal_mutex, nullptr) ||
		pthread_cond_init(&cal_request, nullptr))
	{
		__android_log_assert(nullptr, LOG_TAG,"pthread did big bad"); // *kaboom*
	}
}

InfiCam::~InfiCam(){
	exit_all_theads = true;
	pthread_mutex_lock(&shutter_mutex);
	pthread_cond_signal(&shutter_request);
	pthread_mutex_unlock(&shutter_mutex);
	pthread_join(shutter_thread, nullptr);
}





int InfiCam::connect(const int uvc_fd, int & output_width, int & output_height, settings_callback_t * p_settings_callback) {
	int width, height;
	bool use_raw_logic = false;
	if (dev.connect(uvc_fd, width, height, use_raw_logic)) {
		return 2;
	}
	if(!use_raw_logic) __android_log_assert(nullptr, nullptr,"WTF");
	cam_settings.use_raw_logic = use_raw_logic; //atomics, so we have to do it here

	infiframe = new InfiFrame(cam_settings, width, height);
	packet_reconstruction_buffer = new uint16_t[infiframe->width*infiframe->stream_height];

	output_width = infiframe->width;
	output_height = infiframe->vision_height;
	this->settings_callback = p_settings_callback;

	LOGD("Connected\n");
	return 0;
}

void InfiCam::disconnect(){
	if(infiframe == nullptr) return; //already disconnected
	stream_stop();
	dev.disconnect();

	delete infiframe;
	infiframe = nullptr;
	delete packet_reconstruction_buffer;
	packet_reconstruction_buffer = nullptr;
}

void InfiCam::start_raw_frame_validation(){
	if(!cam_settings.use_raw_logic || !smart_calibration_enabled.load()){
		is_calibrating = false;
		return;
	}
	range_validation_active = true;
	range_validation_started = false;
	range_validation_retry_count = 0;
	range_validation_retry_not_before = steady_clock::now();
	range_validation_start_variance = NAN;
	range_validation_start_valid_count = 0;
	is_calibrating = true;
}

void InfiCam::set_raw_validation_shutter(bool closed){
	pthread_mutex_lock(&shutter_mutex);
	if(closed){
		range_validation_holds_shutter = true;
		keep_shutter_closed = true;
		pthread_cond_signal(&shutter_request);
	} else if(range_validation_holds_shutter){
		range_validation_holds_shutter = false;
		keep_shutter_closed = false;
	}
	pthread_mutex_unlock(&shutter_mutex);
}


int InfiCam::stream_start(frame_callback_t *cb) {
	LOGD("Attempting to start stream\n");
	frame_callback = cb;
	if (dev.stream_start(uvc_frame_callback, this,
						 infiframe->width, infiframe->stream_height,
						 cam_settings.use_raw_logic)) {
		dev.stream_stop();
		return 1;
	}
	is_ready = true;

	Utils::sleep(300); //from official implementation

	if(cam_settings.use_raw_logic){
		is_calibrating = true;
		LOGD("Sending sensor calibration download request.\n");
		pthread_mutex_lock(&command_mutex);
		dev.set_zoom_abs(CMD_DOWNLOAD_CAL);
		pthread_mutex_unlock(&command_mutex);
	}

	LOGD("Sending temp mode command\n");
	pthread_mutex_lock(&command_mutex);
	dev.set_zoom_abs(CMD_MODE_TEMP);
	pthread_mutex_unlock(&command_mutex);

	//If the calibration isn't fully loaded after a while, try again from zero. Modified from the official implementation.
	if(cam_settings.use_raw_logic){
		steady_clock::time_point download_start = steady_clock::now();
		int attempts = 1; //we already sent one request
		while(infiframe->gain_k_line_counter != infiframe->vision_height){
			if(attempts > 3){
				dev.stream_stop();
				is_ready = false;
				is_calibrating = false;
				return 2;
			}
			Utils::sleep(100);
			int elapsed = Utils::ms_since(download_start);
			if(elapsed > 5000){//5000ms (7*25 lines per sec, should be plenty of times)
				LOGW("Timeout. Sending sensor calibration download request again.\n");
				infiframe->gain_k_line_counter = 0;
				pthread_mutex_lock(&command_mutex);
				dev.set_zoom_abs(CMD_DOWNLOAD_CAL);
				pthread_mutex_unlock(&command_mutex);
				download_start = steady_clock::now();
				attempts += 1;
			}
		}
		start_raw_frame_validation();
	}

	settle_start_time = steady_clock::now();
	refresh_auto_shut_interval();

	LOGD("Stream started successfully.\n");
	return 0;
}

void InfiCam::stream_stop() {
	if(!is_ready) { return; } //not running
	is_ready = false;
	is_calibrating = false;
	is_shutter_calibrating = false;
	suppress_calibration = false;
	suppressed_calibration_pending = false;
	range_validation_active = false;
	range_validation_started = false;
	range_validation_retry_count = 0;
	set_raw_validation_shutter(false);
	dev.stream_stop();
}



/*
 * "shutter_request" activates shutter immediately.
 * "shutter_request" is guaranteed to close the shutter, at worst we keep it closed longer than expected.
 */
void * InfiCam::shutter_thread_func(void * arg){
	auto * t = (InfiCam*) arg; //t -> this
	while(!t->exit_all_theads){
		pthread_mutex_lock(&t->shutter_mutex);
		pthread_cond_wait(&t->shutter_request, &t->shutter_mutex);
		if(t->exit_all_theads){
			pthread_mutex_unlock(&t->shutter_mutex);
			break;
		}
		t->dev.set_zoom_abs(CMD_SHUTTER);

		while(t->keep_shutter_closed){
			pthread_mutex_unlock(&t->shutter_mutex);
			Utils::sleep(100);
			t->dev.set_zoom_abs(CMD_SHUTTER);
			pthread_mutex_lock(&t->shutter_mutex);
		}

		pthread_mutex_unlock(&t->shutter_mutex);
	}
	return nullptr;
}


void InfiCam::calibrate(){
	if(!is_ready) { return; }
	if(suppress_calibration.load()){
		suppressed_calibration_pending = true;
		return;
	}
	suppressed_calibration_pending = false;
	is_calibrating = true;
	is_shutter_calibrating = true;
	pthread_mutex_lock(&shutter_mutex);
	pthread_cond_signal(&shutter_request);
	pthread_mutex_unlock(&shutter_mutex);
	calibration_shutter_time = steady_clock::now();
	infiframe->start_calibration();
	refresh_auto_shut_interval();
}

bool InfiCam::isCalibrating(){
	return is_calibrating.load();
}

bool InfiCam::setCalibrationSuppressed(bool suppress){
	bool was_suppressed = suppress_calibration.exchange(suppress);
	if(!suppress && was_suppressed && suppressed_calibration_pending.exchange(false)){
		return true;
	}
	return false;
}

void InfiCam::setSmartCalibrationEnabled(bool enabled){
	smart_calibration_enabled = enabled;
	if(!enabled){
		range_validation_active = false;
		range_validation_started = false;
		range_validation_retry_count = 0;
		set_raw_validation_shutter(false);
		if(!is_shutter_calibrating.load()){
			is_calibrating = false;
			pthread_mutex_lock(&cal_mutex);
			pthread_cond_signal(&cal_request);
			pthread_mutex_unlock(&cal_mutex);
		}
	}
}

void InfiCam::lock_shutter(){
	if(!is_ready) { return; }
	pthread_mutex_lock(&shutter_mutex);
	keep_shutter_closed = true;
	pthread_cond_signal(&shutter_request);
	pthread_mutex_unlock(&shutter_mutex);
}

void InfiCam::unlock_shutter(){
	if(!is_ready) { return; }
	pthread_mutex_lock(&shutter_mutex);
	keep_shutter_closed = false;
	pthread_mutex_unlock(&shutter_mutex);
}


/*
 * Checks if the sensor output is outside of the normal range. Only use when shutter is closed.
 * On startup and range change, the sensor outputs garbage for a little while. Restart calibration from scratch if so.
 */
bool InfiCam::reset_cal_on_bad_data(InfiCam * t, const uint16_t * frame){
	double avg = 0;
	for(int i = 0 ; i < t->infiframe->width*t->infiframe->vision_height ; i++){
		avg += frame[i];
	}
	avg /= (t->infiframe->width*t->infiframe->vision_height);
	//Detection of the sensor not being ready and outputting bad data.
	if(avg>0x4000*0.75 || avg<0x4000*0.01){	 //We are looking at the shutter and know it's at ambient temperature. //TODO: clean up
		t->calibrate(); //reset calibration to the beginning
		return true;
	}
	return false;
}

/*
 * Receives the raw UVC frames.
 * If non-raw sensor, simply passes it along.
 * If raw sensor, decodes the packets, then applies calibration and pre-processing.
 *
 * Note: Packet decoding is only used by a single sensor with a known resolution. It uses hardcoded values.
 */
void InfiCam::uvc_frame_callback(uvc_frame_t *frame, void *user_ptr){
	auto * t = (InfiCam*) user_ptr;
	uint16_t * final_frame;


	if(t->cam_settings.use_raw_logic){ //Only the T2x V2 generation has this. Decoding uses fixed sizes.
		const int PREAMBLE = 6; //starts at offset 6
		const int HEADER = 6; //offset from marker to data
		const int STRIDE =	0x380C; //marker to marker distance
		const int PAYLOAD = 0x3800; //normal data size

		if(frame->data_bytes < 7*STRIDE){ //We may have junk left-over on connect
			LOGW("Ignoring partial frame (%d/%d)\n",frame->data_bytes,STRIDE*7-1);
			return;
		}
		for(int i = 0 ; i < 7 ; i++){
			int current_marker_offset = PREAMBLE+i*STRIDE;
			uint16_t val = ((uint8_t*)frame->data)[current_marker_offset];
			if(val == 1) { //normal image
				memcpy((uint8_t*)t->packet_reconstruction_buffer+i*PAYLOAD,
					   (uint8_t*)frame->data+current_marker_offset+HEADER,
					   PAYLOAD);
			} else if (val == 2) { //k calibration data packet
				LOGD("Received calibration packet %d/%d\n",t->infiframe->gain_k_line_counter+1,t->infiframe->vision_height);
				if(t->infiframe->gain_k_line_counter < t->infiframe->vision_height){ //calibration data is not fully downloaded
					memcpy((uint8_t*)t->infiframe->gain_k_buffer+(t->infiframe->gain_k_line_counter * t->infiframe->width*sizeof(uint16_t)),
						   (uint8_t*)frame->data + current_marker_offset + HEADER,
						   t->infiframe->width * sizeof(uint16_t));
					t->infiframe->gain_k_line_counter++;
				} else {
					LOGE("Ignoring extra calibration data !\n");
				}
			} else {
				LOGW("Corrupted data packet ! marker %d at position %d\n",val,current_marker_offset);
			}
		}

		final_frame = t->packet_reconstruction_buffer;
	} else {
		final_frame = (uint16_t *)frame->data;

		CameraSettings ref_cam_settings = t->cam_settings;
		t->infiframe->updateSettings(final_frame); //Check registers in the frame to see if camera settings changed
		if(ref_cam_settings != t->cam_settings){ //Signal the app that the camera has new settings.
			t->settings_callback(t->inficam_jni, t->cam_settings);
		}
	}


	if(t->is_shutter_calibrating.load()){ //calibration result is not used on non-raw sensors
		int ms_since_shutter = Utils::ms_since(t->calibration_shutter_time);
		if(ms_since_shutter >= 800){ //Shutter is reopening
			if(!t->infiframe->end_calibration()){
				LOGD("Calibration table was invalid. Restarting calibration.\n");
				t->calibrate();
				return;
			}
			LOGI("Calibration complete\n");
			t->is_shutter_calibrating = false;
			if(t->range_validation_active.load() && t->cam_settings.use_raw_logic){
				LOGI("Starting closed-shutter raw frame validation after calibration %d/%d.\n",
					 t->range_validation_retry_count.load(), RAW_RANGE_VALIDATION_MAX_RETRIES);
				t->set_raw_validation_shutter(true);
				t->range_validation_start_time = steady_clock::now();
				t->range_validation_started = true;
				t->range_validation_start_variance = NAN;
				t->range_validation_start_valid_count = 0;
				t->is_calibrating = true;
			} else {
				t->is_calibrating = false;
				//tell anyone waiting that cal is done
				pthread_mutex_lock(&t->cal_mutex);
				pthread_cond_signal(&t->cal_request);
				pthread_mutex_unlock(&t->cal_mutex);
			}
		}
		else if (ms_since_shutter >= 350) { //Shutter is closed
			if(reset_cal_on_bad_data(t, final_frame)){
				LOGD("Received improbable image. Restarting calibration as sensor has not yet stabilized.\n");
				return;
			}
			CameraSettings ref_cam_settings = t->cam_settings;

			//we update table on every frame during calibration. Simpler to keep code clean that way. Also handles frame drops better by not depending on end_calibration being on time.
			if(!t->infiframe->calibrate_on_frame(final_frame)){ //For non-raw, we receive the new settings from the camera during the table update.
				LOGD("Skipping invalid calibration frame.\n");
				return;
			}

			if(ref_cam_settings != t->cam_settings){ // Signal the app that the camera has new settings. (Only happens on non-raw)
				t->settings_callback(t->inficam_jni, t->cam_settings);
			}
		}
	} else {
		long range_settle_time = Utils::ms_since(t->settle_start_time);
		bool range_is_settling = t->cam_settings.use_raw_logic &&
				range_settle_time < RAW_RANGE_AUTO_SHUTTER_SETTLE_MS;
		if(!t->is_calibrating.load() &&
		   !range_is_settling &&
		   t->auto_shut_data.enable &&
		   Utils::ms_since(t->calibration_shutter_time) > t->auto_shut_data.next_interval){
			t->calibrate();
			t->refresh_auto_shut_interval();
		}

		float temp_array[t->infiframe->width*t->infiframe->vision_height];
		if(t->cam_settings.use_raw_logic){
			if(t->infiframe->gain_k_line_counter < t->infiframe->vision_height){ //calibration data is not fully downloaded
				LOGW("Skipping frame. We won't have the K calibration data.\n");
				return;
			}
			uint16_t calibrated_frame[t->infiframe->width*t->infiframe->vision_height];
			t->infiframe->apply_calibration(final_frame, calibrated_frame);
			t->infiframe->destripe(calibrated_frame);
			t->infiframe->fix_dead_pixels(calibrated_frame);
			t->infiframe->frame_to_temp(calibrated_frame,temp_array);
		} else { //no processing required
			t->infiframe->frame_to_temp(final_frame,temp_array);
		}

		if(t->range_validation_active.load() && t->cam_settings.use_raw_logic){
			if(!t->range_validation_started.load()){
				int wait_ms = Utils::ms_since(t->range_validation_retry_not_before);
				if(t->range_validation_retry_count.load() > 0 &&
				   wait_ms >= 0 &&
				   t->range_validation_retry_count.load() <= RAW_RANGE_VALIDATION_MAX_RETRIES){
					LOGW("Raw range validation retry %d/%d starting after spacing delay.\n",
						 t->range_validation_retry_count.load(), RAW_RANGE_VALIDATION_MAX_RETRIES);
					t->calibrate();
				}
				return;
			}
			int elapsed = Utils::ms_since(t->range_validation_start_time);
			int valid_count = 0;
			float variance = temperature_variance(temp_array,
												  t->infiframe->width*t->infiframe->vision_height,
												  valid_count);
			if(elapsed < RAW_RANGE_VALIDATION_SHUTTER_SETTLE_MS){
				return;
			}
			if(std::isnan(t->range_validation_start_variance)){
				t->range_validation_start_variance = variance;
				t->range_validation_start_valid_count = valid_count;
				LOGI("Raw frame validation start variance=%f valid=%d.\n",
					 variance, valid_count);
				return;
			}
			if(elapsed < RAW_RANGE_VALIDATION_SHUTTER_SETTLE_MS +
					   RAW_RANGE_VALIDATION_OBSERVE_MS){
				return;
			}
			bool too_few_pixels =
					valid_count < (t->infiframe->width*t->infiframe->vision_height) / 2 ||
					t->range_validation_start_valid_count <
					(t->infiframe->width*t->infiframe->vision_height) / 2;
			bool implausibly_uniform =
					variance < RAW_RANGE_MIN_VALID_VARIANCE ||
					t->range_validation_start_variance < RAW_RANGE_MIN_VALID_VARIANCE;
			float variance_delta = fabsf(variance - t->range_validation_start_variance);
			float variance_limit = std::max(RAW_RANGE_VARIANCE_DELTA_ABS,
											std::max(variance,
													 t->range_validation_start_variance) *
											RAW_RANGE_VARIANCE_DELTA_REL);
			bool unstable = too_few_pixels || implausibly_uniform ||
					variance_delta > variance_limit;
			if(unstable){
				int retry_count = t->range_validation_retry_count.load();
				if(retry_count < RAW_RANGE_VALIDATION_MAX_RETRIES){
					t->range_validation_retry_count = retry_count + 1;
					LOGW("Raw frame validation failed after %d ms: start_variance=%f end_variance=%f delta=%f limit=%f start_valid=%d end_valid=%d uniform=%d. Scheduling recalibration %d/%d.\n",
						 elapsed,
						 t->range_validation_start_variance,
						 variance,
						 variance_delta,
						 variance_limit,
						 t->range_validation_start_valid_count,
						 valid_count,
						 implausibly_uniform,
						 retry_count + 1, RAW_RANGE_VALIDATION_MAX_RETRIES);
					t->range_validation_started = false;
					t->set_raw_validation_shutter(false);
					t->range_validation_retry_not_before =
							steady_clock::now() +
							std::chrono::milliseconds(RAW_RANGE_VALIDATION_RETRY_SPACING_MS);
					return;
				}
				LOGW("Raw frame validation still failed after %d retries: start_variance=%f end_variance=%f delta=%f limit=%f start_valid=%d end_valid=%d uniform=%d. Releasing frame.\n",
					 retry_count,
					 t->range_validation_start_variance,
					 variance,
					 variance_delta,
					 variance_limit,
					 t->range_validation_start_valid_count,
					 valid_count,
					 implausibly_uniform);
				t->range_validation_active = false;
				t->range_validation_started = false;
				t->set_raw_validation_shutter(false);
				t->is_calibrating = false;
				pthread_mutex_lock(&t->cal_mutex);
				pthread_cond_signal(&t->cal_request);
				pthread_mutex_unlock(&t->cal_mutex);
				return;
			}
			LOGI("Raw frame validation accepted after %d ms: start_variance=%f end_variance=%f delta=%f limit=%f start_valid=%d end_valid=%d uniform=%d retries=%d.\n",
				 elapsed,
				 t->range_validation_start_variance,
				 variance,
				 variance_delta,
				 variance_limit,
				 t->range_validation_start_valid_count,
				 valid_count,
				 implausibly_uniform,
				 t->range_validation_retry_count.load());
			t->range_validation_active = false;
			t->range_validation_started = false;
			t->set_raw_validation_shutter(false);
			t->is_calibrating = false;
			pthread_mutex_lock(&t->cal_mutex);
			pthread_cond_signal(&t->cal_request);
			pthread_mutex_unlock(&t->cal_mutex);
			return;
		}

		t->frame_callback(t->inficam_jni, temp_array, t->cam_settings); //callback the app with the temperature frame
	}
}




void InfiCam::set_float(const int addr, const float val){
	LOGD("Sending float command addr=%d val=%f\n",addr,val);

	auto *p = (uint8_t *) &val;
	pthread_mutex_lock(&command_mutex);
	dev.set_zoom_abs((((addr + 0) & 0xFF) << 8) | p[0]);
	dev.set_zoom_abs((((addr + 1) & 0xFF) << 8) | p[1]);
	dev.set_zoom_abs((((addr + 2) & 0xFF) << 8) | p[2]);
	dev.set_zoom_abs((((addr + 3) & 0xFF) << 8) | p[3]);
	pthread_mutex_lock(&command_mutex);
}

void InfiCam::set_ushort(const int addr, const uint16_t val) {
	LOGD("Sending ushort command addr=%d val=%d\n",addr,val);

	auto *p = (uint8_t *) &val;
	pthread_mutex_lock(&command_mutex);
	dev.set_zoom_abs((((addr + 0) & 0xFF) << 8) | p[0]);
	dev.set_zoom_abs((((addr + 1) & 0xFF) << 8) | p[1]);
	dev.set_zoom_abs((((addr + 2) & 0xFF) << 8) | p[2]);
	dev.set_zoom_abs((((addr + 3) & 0xFF) << 8) | p[3]);
	pthread_mutex_lock(&command_mutex);
}


std::vector<std::array<float,2>> InfiCam::get_ranges(){
	return cam_settings.get_temperature_ranges();
}


void InfiCam::set_range(const int range) {
	if(!is_ready) { return; }

	LOGD("set_range %d\n",(int)cam_settings.temperature_range);

	if(range < 0 || range > 1){ __android_log_assert(nullptr,"libinficam","BAD temperature range");}
	if(cam_settings.use_raw_logic && range == cam_settings.temperature_range){
		LOGD("Ignoring.");
		return;
	}

	pthread_mutex_lock(&command_mutex);
	dev.set_zoom_abs((range == CameraTemperatureRange::RANGE_120_400) ? CMD_RANGE_400 : CMD_RANGE_120);
	pthread_mutex_unlock(&command_mutex);

	settle_start_time = steady_clock::now();
	refresh_auto_shut_interval();

	if(cam_settings.use_raw_logic){
		if(cam_settings.temperature_range != (CameraTemperatureRange)range){
			cam_settings.temperature_range = (CameraTemperatureRange)range;
			start_raw_frame_validation();
			settings_callback(inficam_jni, cam_settings);
		}
	}
}


void InfiCam::set_correction(const float corr) {
	if(!is_ready) { return; }
	LOGD("set_correction %f (cur %f)\n",corr,(float)cam_settings.temperature_correction);
	if(cam_settings.use_raw_logic){
		if(cam_settings.temperature_correction != corr){
			cam_settings.temperature_correction = corr;
			settings_callback(inficam_jni, cam_settings);
		} else {LOGD("Ignoring\n");}
	} else {
		set_float(ADDR_CORRECTION, corr);
	}
}

void InfiCam::set_temp_reflected(const float t_ref) {
	if(!is_ready) { return; }
	LOGD("set_temp_reflected %f (cur %f)f\n",t_ref,(float)cam_settings.reflection_temperature);
	if(cam_settings.use_raw_logic){
		if(cam_settings.reflection_temperature != t_ref){
			cam_settings.reflection_temperature = t_ref;
			settings_callback(inficam_jni, cam_settings);
		} else {LOGD("Ignoring\n");}
	} else {
		set_float(ADDR_TEMP_REFLECTED, t_ref);
	}
}

void InfiCam::set_temp_air(const float t_air) {
	if(!is_ready) { return; }
	LOGD("set_temp_air %f (cur %f)\n",t_air,(float)cam_settings.air_temperature);
	if(cam_settings.use_raw_logic){
		if(cam_settings.air_temperature != t_air){
			cam_settings.air_temperature = t_air;
			settings_callback(inficam_jni, cam_settings);
		} else {LOGD("Ignoring\n");}
	} else {
		set_float(ADDR_TEMP_AIR, t_air);
	}
}

void InfiCam::set_humidity(const float humi) {
	if(!is_ready) { return; }
	LOGD("set_humidity %f (cur %f)\n",humi,(float)cam_settings.humidity);
	if(cam_settings.use_raw_logic){
		if(cam_settings.humidity != humi){
			cam_settings.humidity = humi;
			settings_callback(inficam_jni, cam_settings);
		} else {LOGD("Ignoring\n");}
	} else {
		set_float(ADDR_HUMIDITY, humi);
	}
}

void InfiCam::set_emissivity(const float emi) {
	if(!is_ready) { return; }
	LOGD("set_emissivity %f (cur %f)\n",emi,(float) cam_settings.emissivity);
	if(cam_settings.use_raw_logic){
		if(cam_settings.emissivity != emi){
			cam_settings.emissivity = emi;
			settings_callback(inficam_jni, cam_settings);
		} else {LOGD("Ignoring\n");}
	} else {
		set_float(ADDR_EMISSIVITY, emi);
	}
}

void InfiCam::set_distance(const uint16_t dist) {
	if(!is_ready) { return; }
	LOGD("set_distance %d (cur %d)\n",dist,(int)cam_settings.distance);
	if(cam_settings.use_raw_logic){
		if(cam_settings.distance != dist){
			cam_settings.distance = dist;
			settings_callback(inficam_jni, cam_settings);
		} else {LOGD("Ignoring\n");}
	} else {
		set_ushort(ADDR_DISTANCE, dist);
	}
}

void InfiCam::store_params(){
	if(!is_ready) { return; }
	if(cam_settings.use_raw_logic){
		LOGE("Saving parameters to RAW sensors is not possible.");
	} else {
		LOGD("Saving parameters to camera.");
		pthread_mutex_lock(&command_mutex);
		dev.set_zoom_abs(CMD_STORE);
		pthread_mutex_unlock(&command_mutex);
	}
}

void InfiCam::refresh_auto_shut_interval(){
	long settle_time = Utils::ms_since(settle_start_time);

	int interval = (int)((float)(auto_shut_data.interval_max-auto_shut_data.interval_min) *
						 pow(std::clamp((float)settle_time / (float)auto_shut_data.interval_max,0.0f,1.0f),1.3f) +
						 (float)auto_shut_data.interval_min);
	LOGD("Shutter interval will be %d ms. Sensor has been settling for %ld ms\n",interval,settle_time);
	auto_shut_data.next_interval = interval;
}

void InfiCam::set_auto_shutter_settings(bool enable, int interval_min, int interval_max){
	auto_shut_data.enable = enable;
	auto_shut_data.interval_min = interval_min;
	auto_shut_data.interval_max = interval_max;
	refresh_auto_shut_interval();
}
