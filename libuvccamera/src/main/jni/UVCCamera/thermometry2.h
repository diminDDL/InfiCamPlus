#ifndef __THERMOMETRY_H__
#define __THERMOMETRY_H__

#include <stdint.h>

void thermometryT4Line2(int width, int param_3,float *temperatureTable,
                        long fourLinePara, float *fpaTmp, float *correction, float *Refltmp,
                        float *Airtmp, float *humi, float *emiss, uint16_t *distance,
                        int cameraLens, float shutterFix, int rangeMode);

#endif /* __THERMOMETRY_H__ */
