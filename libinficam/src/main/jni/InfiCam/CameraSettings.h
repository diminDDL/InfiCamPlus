#ifndef INFICAM_CAMERASETTINGS_H
#define INFICAM_CAMERASETTINGS_H


#include <stdint.h>
#include <atomic>
#include <vector>


typedef enum{
	RANGE_N20_120 = 0, // -20:120
	RANGE_120_400 = 1, // 120:400
} CameraTemperatureRange; //we only have one set for now

class CameraSettings {
		static inline std::vector<std::array<float,2>> temperature_ranges={{-20.0f,120.0f},{120.0f,400.0f}};

public:

	std::atomic<float> temperature_correction{};
	std::atomic<float> emissivity{};
	std::atomic<float> humidity{};
	std::atomic<float> air_temperature{};
	std::atomic<float> reflection_temperature{};
	std::atomic<uint16_t> distance{};
	std::atomic<CameraTemperatureRange> temperature_range{};
	std::atomic<int> lens{};
	std::atomic<bool> use_raw_logic{}; //Only used by T2x V2 for now.
	std::atomic<float> max_temperature_clipping{};

	CameraSettings();
	CameraSettings(const CameraSettings& other);


	bool operator==(const CameraSettings& other) const;
	bool operator!=(const CameraSettings& other) const;
	CameraSettings& operator=(const CameraSettings& other);

	std::vector<std::array<float,2>> get_temperature_ranges(); //2D array, see temperature_ranges
private:
	void copy_helper(const CameraSettings& other);
};


#endif //INFICAM_CAMERASETTINGS_H
