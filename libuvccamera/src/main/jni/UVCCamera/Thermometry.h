#ifndef THERMOMETRY_H_
#define THERMOMETRY_H_

class Thermometry {
private:
    float Fix_, Distance_, refltmp_, airtmp_, Humi_, Emiss_;
    float fpatmp_, fpaavg_, orgavg_, coretmp_;
    float flt_100133AC, flt_100133A8, flt_100033A0;
    float flt_10003360, flt_1000335C, flt_1000339C, flt_100033A4, flt_10003398;
    float flt_10003394, temperatureLUT[16384], flt_10003378, flt_1000337C;
    int type_, dev_type_, Height_, Width_;
    static int readParaFromDevFlag = 1;
};

#endif /* THERMOMETRY_H_ */
