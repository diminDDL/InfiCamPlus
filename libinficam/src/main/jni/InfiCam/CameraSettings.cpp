#include "CameraSettings.h"


CameraSettings::CameraSettings() {
    temperature_correction = 0.0f;
    emissivity = 0.98f;
    humidity = 0.45f;
    air_temperature = 25.0f;
    reflection_temperature = 25.0f;
    distance = 1;
    temperature_range = CameraTemperatureRange::RANGE_N20_120;
    lens = 130;// 13.0mm by default. Never changed by official app (?). The code can handle 8.6mm as well.
    use_raw_logic = false;
}

CameraSettings::CameraSettings(const CameraSettings& other) {
    copy_helper(other);
}


bool CameraSettings::operator==(const CameraSettings& other) const {
    if(temperature_correction != other.temperature_correction ||
       emissivity != other.emissivity ||
       humidity != other.humidity ||
       air_temperature != other.air_temperature ||
       reflection_temperature != other.reflection_temperature ||
       distance != other.distance ||
       temperature_range != other.temperature_range ||
       lens != other.lens ||
       use_raw_logic != other.use_raw_logic ||
       max_temperature_clipping != other.max_temperature_clipping){
           return  false;
       }
    return true;
}
bool CameraSettings::operator!=(const CameraSettings& other) const {
    return !(*this==other);
}

CameraSettings& CameraSettings::operator=(const CameraSettings& other) {
    if(this != &other){
        copy_helper(other);
    }
    return *this;
}

void CameraSettings::copy_helper(const CameraSettings& other){
    temperature_correction.store(other.temperature_correction.load());
    emissivity.store(other.emissivity.load());
    humidity.store(other.humidity.load());
    air_temperature.store(other.air_temperature.load());
    reflection_temperature.store(other.reflection_temperature.load());
    distance.store(other.distance.load());
    temperature_range.store(other.temperature_range.load());
    lens.store(other.lens.load());
    use_raw_logic.store(other.use_raw_logic.load());
    max_temperature_clipping.store(other.max_temperature_clipping.load());
}

std::vector<std::array<float,2>> CameraSettings::get_temperature_ranges(){ // NOLINT(*-convert-member-functions-to-static) //Keep this for future camera models
    return temperature_ranges;
}