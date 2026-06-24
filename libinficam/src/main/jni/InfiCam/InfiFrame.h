#ifndef INFIFRAME_H_
#define INFIFRAME_H_


#include "CameraSettings.h"

#include <cstdint>
#include <cstddef> /* size_t */
#include <string>


/* A class to help analyze frames coming from an InfiRay thermal camera, this is all the passive
 *   stuff, no talking to the camera.
 */
class InfiFrame {

public:

	InfiFrame(CameraSettings & cam_settings, int stream_width, int stream_height);
	~InfiFrame();

	/* Generate lookup table for converting to Celsius, calls update().
	 * For realtime use I suggest using it only when the shutter closes as a calibration step, as
	 *   it is not a very fast function.
	 */
	void start_calibration();
	void calibrate_on_frame(const uint16_t * frame);
	void end_calibration();

	//for raw sensors
	void apply_calibration(const uint16_t * source_frame, uint16_t * calibrated_frame); //raw only
	void destripe(uint16_t * frame) const;
	void fix_dead_pixels(uint16_t * frame) const; //firmware provided k frame indicates if pixel is dead

	//for non-raw sensors
	void updateSettings(const uint16_t *fourLinePara);

	//sensor value to temperature
	void frame_to_temp(const uint16_t * frame, float * temp) const;


	/* InifiCam decodes packets into this buffer. Give it direct access for simplicity */
	uint16_t* gain_k_buffer = nullptr; //only used for raw sensor
	int gain_k_line_counter = 0;

	const int width;
	const int stream_height;
	const int vision_height;

private:

	static const int METADATA_HEIGHT = 4;

	static const int DESTRIPE_CONTRAST_LIMIT=16; //from official app
	static const int DESTRIPE_VOTE_CAP=4; //custom, may need adjustment


	CameraSettings & cam_settings; //shared copy of InfiCam's cam_settings

	uint32_t* offset_b_buffer_accu = nullptr; //32bit to accumulate frames during calibration
	uint16_t* offset_b_buffer = nullptr; // 16bit for processed result
	int offset_b_buffer_counter = 0;

	float temperature_table[0x4000]{};

	static int rangeToDeviceRange(CameraTemperatureRange t);
	void updateTable(const uint16_t * frame);

	void destripe_standard(uint16_t * frame) const;
#if defined(__aarch64__) || defined(__arm__)
	void destripe_neon(uint16_t * frame) const;
#endif

	/* Create the conversion table, also reads the settings from the camera. Perfect re-creation of the official function (but with float calculations). Multiple very similar functions were merged into one. The mess is expected. */
	static void thermometryT4Line(int width, int height,
						   float *temperatureTable, //output, post-nuc sensor value to temperature table
						   const uint16_t *fourLinePara, //input, pointer to last 4 lines of the frame
						   float *floatFpaTmp, //output, sensor or shutter temp (not sure)
						   float *correction, //input and output, temperature correction in C
						   float *Refltmp, //input and output
						   float *Airtmp, //input and output
						   float *humi, //input and output
						   float *emiss, //input and output
						   uint16_t *distance, //input and output
						   int cameraLens, //input, camera lens in .1mm
						   float shutterFix, //input, unused in RAW sensors.
						   int rangeMode,//input, 120 or 400
						   int isNewProduct,//input, wherever to use the non-raw or raw codepath
						   int isOffline//input, a third codepath, not sure what for
	) ; //different code paths depending on the camera model (isOffline is a mystery)
	/* Subfunction of thermometryT4Line, handles offsets for specific serial/model numbers */
	static void ComparePN(int width,
				   const uint16_t *fourLinePara,
				   float *floatFpaTmp,
				   char *output_version_string) ;



};


#endif /* INFIFRAME_H_ */
