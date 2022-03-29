#ifndef __INFICAM_H__
#define __INFICAM_H__

#include "UVCDevice.h"
#include "InfiFrame.h"
#include <cstdint>
#include <cmath> /* NAN */
#include <pthread.h>

/* This one is for actually interacting with the thermal camera, wraps UVCDevice and InfiFrame.
 *
 * Note that because Android does not allow libusb to do device discovery related stuff, there is
 *   no way to detect when the device has disconnected here, this has to be implemented separately.
 *
 * The callback given to stream_start() can block as long as it wants since libuvc has a separate
 *   thread dedicated to calling the user callback, in the worst case a few frames are missed.
 */
class InfiCam {
	typedef void (frame_callback_t)(InfiCam *cam, float *temp, uint16_t *raw, void *user_ptr);

	UVCDevice dev;
	frame_callback_t *frame_callback;
	void *frame_callback_arg;
	float *frame_temp = NULL;
	pthread_mutex_t frame_callback_mutex;
	int connected = 0, streaming = 0, table_invalid;

	static const int CMD_SHUTTER = 0x8000;
	static const int CMD_MODE_TEMP = 0x8004;
	static const int CMD_MODE_YUV = 0x8005;
	static const int CMD_RANGE_120 = 0x8020;
	static const int CMD_RANGE_400 = 0x8021;
	static const int CMD_STORE = 0x80FF;

	static const int ADDR_CORRECTION = 0;
	static const int ADDR_TEMP_REFLECTED = 4;
	static const int ADDR_TEMP_AIR = 8;
	static const int ADDR_HUMIDITY = 12;
	static const int ADDR_EMISSIVITY = 16;
	static const int ADDR_DISTANCE = 20;

	static void uvc_callback(uvc_frame_t *frame, void *user_ptr);
	void set_float(int addr, float val); /* Write to camera user memory, needs lock. */

public:
	static const int palette_len = InfiFrame::palette_len;
	/* InfiFrame class gets updated before each stream CB with info relevant to the frame.
	 * The width and height in there are valid after connect().
	 */
	InfiFrame infi;

	~InfiCam();

	int connect(int fd); /* Closes the FD on disconnect. */
	void disconnect(); /* Opening a new connection will close the previous one if it exists. */

	/* Stream CB arguments valid until return, CB runs on it's own thread. Trying to start a stream
	 *   when already streaming returns an error. Do beware of some possible thread safety issues
	 *   as set_palette() for example will modify the InfiFrame instance passed to the callback
	 *   and calling update() on that after set_range()/set_distance_multipler() or changing
	 *   any of it's parameters from another thread is not guarded against. The update_table()
	 *   and such done before calling the callback are guarded against the functions below with a
	 *   mutex so they should be okay to use whenever as long as you don't do something crazy like
	 *   call infi.update() in a different thread.
	 */
	int stream_start(frame_callback_t *cb, void *user_ptr);
	void stream_stop(); /* Attempting to stop stream is okay even when no stream. */

	/* Set range, valid values are 120 and 400 (see InfiFrame class).
	 * Changes take effect after update/update_table().
	 */
	void set_range(int range);

	/* Distance multiplier, 3.0 for 6.8mm lens, 1.0 for 13mm lens.
	 * Changes only take effect after update_table().
	 */
	void set_distance_multiplier(float dm);

	/* Setting parameters, only works while streaming.
	 * Changes only take effect after update_table().
	 */
	void set_correction(float corr);
	void set_temp_reflected(float t_ref);
	void set_temp_air(float t_air);
	void set_humidity(float humi);
	void set_emissivity(float emi);
	void set_distance(float dist);
	void set_params(float corr, float t_ref, float t_air, float humi, float emi, float dist);
	void store_params(); /* Store user memory to camera so values remain when reconnecting. */

	void update_table();
	void calibrate();

	void set_palette(uint32_t *palette); /* Length must be palette_len. */
};

#endif /* __INFICAM_H__ */
