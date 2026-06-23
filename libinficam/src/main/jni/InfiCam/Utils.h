#ifndef INFICAM_UTILS_H
#define INFICAM_UTILS_H

#include <algorithm>

#include <android/log.h>
using steady_clock = std::chrono::steady_clock; //names are long enough already


#define LOG_TAG "libinficam"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


/* Keep math clean */
template <typename T>
auto min(const T& a, const T& b) {
    return std::min(a, b);
}
template <typename T>
auto max(const T& a, const T& b) {
    return std::max(a, b);
}

class Utils {
public:
    static void sleep(int milliseconds);
    static long ms_since(steady_clock::time_point ref);

#if defined(__arm__) //arm 32bit
    static bool has_neon();
#endif
};


#endif //INFICAM_UTILS_H
