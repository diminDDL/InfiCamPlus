#ifndef __THERMOMETRY_H__
#define __THERMOMETRY_H__

#include <stdint.h>

class Thermometry {
public:
    float Fix_, Distance_, refltmp_, airtmp_, Humi_, Emiss_;
    float fpatmp_, fpaavg_, orgavg_, coretmp_;
    float flt_100133A8, flt_100033A0;
    float flt_10003360, flt_1000335C, flt_1000339C, flt_100033A4, flt_10003398;
    float flt_10003394, temperatureLUT[16384], flt_10003378, flt_1000337C;
    int type_, dev_type_, Height_, Width_;

    void sub_10001010();
    unsigned int sub_10001180(float a1, int16_t cx);

public:
    int readParaFromDevFlag = 1; // TODO must this be here?
    void tobj(double h, double t_atm, double d, double e, double t_refl, int cx);

    void UpdateFixParam(float Emiss, float refltmp, float airtmp, float Humi, unsigned short Distance, float Fix);
    void GetFixParam(float *Emiss, float *refltmp, float *airtmp, float *Humi, unsigned short *Distance, float *Fix);
    void UpdateParam(int type, uint8_t *pbuff);
    int DataInit(int Width, int Height);
    void GetTmpData(int type, uint8_t *pbuff, float *maxtmp, int *maxx, int *maxy, float *mintmp, int *minx, int *miny, float *centertmp, float *tmparr, float *alltmp);
    void GetDevData(float *fpatmp, float *coretmp, int *fpaavg, int *orgavg);
};

#endif /* __THERMOMETRY_H__ */
