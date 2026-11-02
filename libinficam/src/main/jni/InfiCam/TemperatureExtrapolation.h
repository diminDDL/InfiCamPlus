#ifndef INFICAM_TEMPERATURE_EXTRAPOLATION_H
#define INFICAM_TEMPERATURE_EXTRAPOLATION_H

#include <algorithm>
#include <cmath>
#include <cstddef>

namespace TemperatureExtrapolation {
constexpr float INVALID_TEMPERATURE = -273.0f;
constexpr double ABSOLUTE_ZERO = -273.15;

struct Fit {
    size_t first_valid = 0;
    size_t fit_end = 0; // exclusive
    double slope = 0;
};

// Fill only the invalid lower prefix. All fitted parameters come from this
// camera's current native table, after its environment/range corrections.
// The bounded rational tail preserves ordering and joins the native endpoint:
// T(x) = Tmin + (T0-Tmin) / (1 + slope*(x0-x)/(T0-Tmin)).
// Its temperatures are estimates; it cannot compensate a drifting calibration.
inline bool fillLowTail(float *table, size_t count, Fit &fit) {
    fit = {};
    if(!table || count == 0) return false;
    size_t first = 0;
    while(first < count && table[first] == INVALID_TEMPERATURE) ++first;
    if(first == count) return false;
    for(size_t i = first; i < count; ++i){
        if(!std::isfinite(table[i]) || table[i] == INVALID_TEMPERATURE ||
           table[i] <= ABSOLUTE_ZERO || (i > first && table[i] < table[i-1])) return false;
    }
    fit.first_valid = first;
    fit.fit_end = first;
    if(first == 0) return true; // complete native table; do not change it

    constexpr size_t MIN_SAMPLES = 16;
    constexpr double FIT_SPAN_C = 40.0;
    if(count-first < MIN_SAMPLES) return false;
    size_t end = first+1;
    while(end < count && (end-first < MIN_SAMPLES || table[end-1]-table[first] < FIT_SPAN_C)) ++end;
    double sum_xx = 0, sum_xy = 0;
    for(size_t i = first+1; i < end; ++i){
        const double dx = i-first;
        sum_xx += dx*dx;
        sum_xy += dx*((double)table[i]-table[first]);
    }
    const double slope = sum_xy/sum_xx;
    const double kelvin = (double)table[first]-ABSOLUTE_ZERO;
    if(!std::isfinite(slope) || slope <= 0 || !std::isfinite(kelvin) || kelvin <= 0) return false;
    fit.fit_end = end;
    fit.slope = slope;
    for(size_t i = 0; i < first; ++i){
        table[i] = (float)(ABSOLUTE_ZERO + kelvin/(1.0+slope*(first-i)/kelvin));
    }
    return true;
}
}
#endif
