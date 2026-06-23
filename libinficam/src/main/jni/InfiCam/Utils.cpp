#include <ctime>
#include "Utils.h"

void Utils::sleep(const int milliseconds){
    struct timespec wait_time = {milliseconds/1000, milliseconds%1000*1000000};
    nanosleep(&wait_time, nullptr);
}

long Utils::ms_since(steady_clock::time_point ref){
    return std::chrono::duration_cast<std::chrono::milliseconds>(steady_clock::now() - ref).count(); //Why is C++ like this?
}

#if defined(__arm__) //arm 32bit
#include <sys/auxv.h>
#include <asm/hwcap.h>
bool Utils::has_neon(){
    return (getauxval(AT_HWCAP) & HWCAP_NEON) != 0;
}
#endif