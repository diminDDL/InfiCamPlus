#ifndef __THERMOMETRY_H__
#define __THERMOMETRY_H__

#include <stdint.h>

typedef uint16_t ushort;

void thermometryT4Line2(int width,int height,float *temperatureTable,ushort *fourLinePara,
                        float *floatFpaTmp,float *correction,float *Refltmp,float *Airtmp,float *humi,
                        float *emiss,ushort *distance,int cameraLens,float shutterFix,int rangeMode);

#endif /* __THERMOMETRY_H__ */
