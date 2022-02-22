/*
 * Based on: https://github.com/ebachard/ht301_hacklib/
 */
/*********************************************************************
* Software License Agreement (BSD License)
*
*  Copyright (C) 2012 Ken Tossell
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions
*  are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above
*     copyright notice, this list of conditions and the following
*     disclaimer in the documentation and/or other materials provided
*     with the distribution.
*   * Neither the name of the author nor other contributors may be
*     used to endorse or promote products derived from this software
*     without specific prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
*  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
*  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
*  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
*  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
*  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
*  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
*  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
*  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
*  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
*  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
*  POSSIBILITY OF SUCH DAMAGE.
*********************************************************************/

#include <Thermometry.h>
#include <math.h>
#include <stdint.h>

#include "utilbase.h" // TODO temporary

// see: https://doi.org/10.3390/s17081718

double absz = -273.15;
double zeroc = 273.15;

/* I find it strange the equations wvc and atmt are based on work with degrees Celcius without
 *   seemingly involving Kelvin at all. I've verified they match ht301_hacklib and ht301_ircam's
 *   implementations and the graphs in https://doi.org/10.3390/s17081718
 */

/* Water vapor coefficient from humidity and ambient temperature. */
static double wvc(double h, double t_atm) {
    double h1 = 1.5587, h2 = 0.06939, h3 = -2.7816e-4, h4 = 6.8455e-7;
    return h * exp(h1 + h2 * t_atm + h3 * pow(t_atm, 2) + h4 * pow(t_atm, 3));
}

/* Transmittance of the atmosphere from humitity, ambient temperature and distance. */
static double atmt(double h, double t_atm, double d) {
    double k_atm = 1.9, nsqd = -sqrt(d), sqw = sqrt(wvc(h, t_atm));
    double a1 = 0.006569, a2 = 0.01262; /* Athmospheric attenuation without water vapor. */
    double b1 = -0.002276, b2 = -0.00667; /* Attenuation for water vapor. */
    return k_atm * exp(nsqd * (a1 + b1 * sqw)) + (1.0 - k_atm) * exp(nsqd * (a2 + b2 * sqw));
}

/* Object temperature from humidity, ambient temperature, distance, emissivity and reflected temperature. */
void Thermometry::tobj(double h, double t_atm, double d, double e, double t_refl, int cx) {
    double atm = atmt(h, t_atm, d);
    // TODO why does python/infiray version not use boltzman and + instead of -? what about absz?
    //double bm = 0.0000000567; /* Stefan-Boltzmann constant. */
    //double dividend = (1.0 - e) * atm * bm * pow(t_refl/* - absz*/, 4) - (1.0 - atm) * bm * pow(t_atm/* - absz*/, 4);
    //double divisor = e/* * bm*/ * atm;
    double dividend = (1.0 - e) * atm * pow(t_refl + zeroc, 4) + (1.0 - atm) * pow(t_atm + zeroc, 4);
    double divisor = e * atm;

    double l_flt_1000337C = flt_1000335C / (2.0 * flt_10003360);
    double l_flt_1000337C_2 = pow(l_flt_1000337C, 2);
    double v23 = flt_10003360 * pow(coretmp_, 2) + flt_1000335C * coretmp_;
    double v22 = flt_1000339C * pow(fpatmp_, 2) + flt_10003398 * fpatmp_ + flt_10003394;
    double v2 = 390.0 - fpatmp_ * 7.05; // TODO python version has option to set 0, and int() around it
    //double v2 = 0;

    for (int i = 0; i < 16384; ++i) {
        double ttot = sqrt(((i - (cx - v2)) * v22 + v23) / flt_10003360 + l_flt_1000337C_2) - l_flt_1000337C - absz;
        double wtot = pow(ttot, 4);
        double tobj = pow((wtot - dividend) / divisor, 1.0 / 4.0) + absz;

        double dc = (((d >= 20.0) ? 20.0 : d) * 0.85 - 1.125) / 100.0;
        double res = tobj + dc * (tobj - t_atm);
        temperatureLUT[i] = res;
    }
}

void Thermometry::sub_10001010() {
    float v9, v11;
    double v10;

    /*
    *divisor = 1.0 / (atm * emiss); // this is reciprocal of "divisor" in tobj()
    dVar2 = pow((double)(t_refl + 273.15), 4.0);
    //atm = *atmp; // no-op
    dVar3 = pow((double)(t_atmosphere + 273.15), 4.0);
    *dividend = (float)dVar3 * (1.0 - *atmp) + (1.0 - emiss) * (float)dVar2 * atm; // divident as in tobj()
    return;
     */

    float atm = atmt(Humi_, airtmp_, Distance_);
    divisor = 1 / (Emiss_ * atm);
    v11 = pow(airtmp_ + 273.15, 4.0);
    v9 = pow(refltmp_ + 273.15, 4.0);
    v10 = v9 * (1.0 - Emiss_) * atm;
    dividend = v11 * (1.0 - atm) + v10;
}

unsigned int Thermometry::sub_10001180(float a1, int16_t cx) {
    int16_t v2;
    uint16_t v3, v4;
    int v5, v19, v21;
    float *p, v7, v8, v9, v11, v13, v14, v15, v17, v18, v20, v22, v23;
    double v12, v16;
    unsigned int result;

    v23 = flt_10003360 * a1 * a1 + a1 * flt_1000335C;
    v22 = flt_1000339C * flt_100033A4 * flt_100033A4 + flt_10003398 * flt_100033A4 + flt_10003394;
    if (type_)
        v2 = 0;
    else v2 = (signed int) (390.0 - flt_100033A4 * 7.05);
    v3 = Distance_;
    v4 = cx - v2;
    v5 = -v4;
    v19 = -v4;
    p = temperatureLUT;
    while (p - temperatureLUT < 16384) {
        v7 = (double) v19 * v22 + v23;
        v8 = v7 / flt_10003360 + flt_10003378;
        v9 = sqrt(v8);
        result = 4;
        v11 = v9 - flt_1000337C;
        v20 = v11 + 273.15; // TODO meant to be 0C in kelvin?
        v17 = 1.0;
        while (1) {
            v12 = v20;
            if (result & 1)
                v17 = v17 * v12;
            result >>= 1;
            if (!result)
                break;
            v20 = v12 * v12;
        }
        v13 = v17 - dividend;
        v14 = v13 * divisor;
        v15 = pow(v14, 0.25);
        v18 = v15 - 273.15;
        if (v3 >= 20)
            v21 = 20;
        else
            v21 = v3;
        ++v5;
        v16 = (double) v21 * 0.85;
        v19 = v5;
        *p = v18 + (v16 - 1.125) * (v18 - airtmp_) / 100.0;
        ++p;
    }
    return result;
}

void Thermometry::UpdateFixParam(float Emiss, float refltmp, float airtmp, float Humi, unsigned short Distance, float Fix) {
    Fix_ = Fix;
    Distance_ = Distance;
    refltmp_ = refltmp;
    airtmp_ = airtmp;
    Humi_ = Humi;
    Emiss_ = Emiss;
    sub_10001010();
}

void Thermometry::GetFixParam(float *Emiss, float *refltmp, float *airtmp, float *Humi, unsigned short *Distance, float *Fix) {
    *Fix = Fix_;
    *refltmp = refltmp_;
    *airtmp = airtmp_;
    *Humi = Humi_;
    *Emiss = Emiss_;
    *Distance = Distance_;
}

void Thermometry::UpdateParam(int type, uint8_t *pbuff) {
    int v2, v3, v5, v7, v11, typea, typeb;
    double v4;
    float v6, v8, v9, v10, v12, v13;

    type_ = type;
    v2 = Height_ + 3;
    if (!dev_type_)
        v2 = Height_ + 1;
    v3 = Width_ * v2;
    v4 = (double) (*(uint16_t *) &pbuff[2 * Width_ * Height_ + 2] - 8617) / 37.682; // TODO depends on camera
    v5 = *(uint16_t *) &pbuff[2 * v3];
    typeb = *(uint16_t *) &pbuff[2 * v3 + 2];
    v6 = *(float*) &pbuff[2 * v3 + 6]; // +6
    v7 = v3 + 127;
    v3 += 5;
    flt_10003360 = v6;
    v8 = *(float *) &pbuff[2 * v3]; // +10
    v3 += 2;
    flt_1000335C = v8;
    v9 = *(float *) &pbuff[2 * v3]; // +14
    v3 += 2;
    flt_1000339C = v9;
    flt_10003398 = *(float *) &pbuff[2 * v3]; // +18
    flt_10003394 = *(float *) &pbuff[2 * v3 + 4]; // +22
    flt_100033A4 = 20.0 - v4;
    *(float *) &typea = (double) typeb / 10.0 - 273.15; // TODO meant to be 0C in kelvin?
    if (readParaFromDevFlag) {
        Fix_ = *(float *) &pbuff[2 * v7];
        v10 = *(float *) &pbuff[2 * v7 + 4];
        v11 = v7 + 2;
        refltmp_ = v10;
        v12 = *(float *) &pbuff[2 * v11 + 4];
        v11 += 2;
        airtmp_ = v12;
        v13 = *(float *) &pbuff[2 * v11 + 4];
        v11 += 4;
        Humi_ = v13;
        Emiss_ = *(float *) &pbuff[2 * v11];
        Distance_ = *(uint16_t *) &pbuff[2 * v11 + 4];
        readParaFromDevFlag = 0;
    }
    flt_1000337C = flt_1000335C / (flt_10003360 + flt_10003360);
    flt_10003378 = flt_1000335C * flt_1000335C / (flt_10003360 * (4.0 * flt_10003360));
    sub_10001010();
    // typea is just coretmp
    sub_10001180(*(float *) &typea, v5); // bug in IDA -- TODO (netman) wtf did they mean by "bug in IDA?"
}

int Thermometry::DataInit(int Width, int Height) {
    int result = 0;

    if (Width == 1280) {
        Width_ = 1280;
        dev_type_ = 4;
        Height_ = 1024;
        result = 1;
    } else if (Width == 1024) {
        dev_type_ = 3;
        Width_ = 1024;
        Height_ = 768;
        result = 1;
    } else if (Width == 640) {
        dev_type_ = 2;
        Width_ = 640;
        Height_ = 512;
        result = 1;
    } else if (Width == 384) {
        dev_type_ = 1;
        Width_ = 384;
        Height_ = 288;
        result = 1;
    } else if (Width == 256) { // NOTE Added by netman.
        dev_type_ = 0;
        Width_ = 256;
        Height_ = 192;
        result = 1;
    } else if (Width == 240) {
        dev_type_ = 0;
        Width_ = 240;
        Height_ = 180;
        result = 1;
    } else {
        // TODO (netman) What if none found?
    }
    return result;
}

void Thermometry::GetTmpData(int type, uint8_t *pbuff, float *maxtmp, int *maxx, int *maxy, float *mintmp, int *minx, int *miny, float *centertmp, float *tmparr, float *alltmp) {
    unsigned char *v11;
    int v12, v14, v15, v16, v18, v22, v23;
    uint16_t *v13;
    float *v17, *v19;
    unsigned int v20;
    uint64_t v21;
    uint16_t pbuffa;

    v11 = pbuff;
    v12 = Width_ * Height_;
    v13 = (uint16_t *) &pbuff[2 * Width_ * Height_];
    v14 = 3;
    fpatmp_ = 20.0 - ((double) *(uint16_t *) &pbuff[2 * Width_ * Height_ + 2] - 8617) / 37.682; // TODO depends on camera
    if (!dev_type_)
        v14 = 1;
    pbuffa = *(uint16_t *) &v13[v14 * Width_ + 2]; //starts at the third line, plus 2 chars of metadata
    v15 = v13[8];
    fpaavg_ = *v13;
    v16 = v13[12];
    orgavg_ = v15;
    coretmp_ = ((double) pbuffa) / 10.0 - 273.15; // TODO meant to be 0C in kelvin?

    // TODO this is just for test
    /*int v2 = Height_ + 3;
    if (!dev_type_)
        v2 = Height_ + 1;
    tobj(Humi_, airtmp_, Distance_, Emiss_, refltmp_, *(uint16_t *) &pbuff[2 * Width_ * v2]);*/

    *centertmp = temperatureLUT[v16] + Fix_;
    *maxtmp = temperatureLUT[v13[4]] + Fix_;
    *mintmp = temperatureLUT[v13[7]] + Fix_;
    *maxx = v13[2];
    *maxy = v13[3];
    *minx = v13[5];
    *miny = v13[6];
    *tmparr = temperatureLUT[v13[13]] + Fix_;
    tmparr[1] = temperatureLUT[v13[14]] + Fix_;
    tmparr[2] = temperatureLUT[v13[15]] + Fix_;
    if (!type) {
        // TODO (netman)
        //memcpy(alltmp, temperatureLUT, sizeof(temperatureLUT));
    }
    /*
    v17 = alltmp;
    v18 = 0;
    if (v12 >= 4)
    {
        v19 = alltmp + 2;
        v20 = ((unsigned int)(v12 - 4) >> 2) + 1;
        v21 = (uint64_t)(v11 + 4);
        v18 = 4 * v20;
        do
        {
            v22 = *(uint16_t*)(v21 - 4);
            v21 += 8;
            v19 += 4;
            --v20;
            *(v19 - 6) = temperatureLUT[v22] + Fix_;
            *(v19 - 5) = temperatureLUT[*(uint16_t*)(v21 - 10)] + Fix_;
            *(v19 - 4) = temperatureLUT[*(uint16_t*)(v21 - 8)] + Fix_;
            *(v19 - 3) = temperatureLUT[*(uint16_t*)(v21 - 6)] + Fix_;
        } while (v20);
        v17 = alltmp;
    }
    for (; v18 < v12; v17[v18 - 1] = temperatureLUT[v23] + Fix_)
        v23 = *(uint16_t*)& v11[2 * v18++];
}
    */
}

void Thermometry::GetDevData(float *fpatmp, float *coretmp, int *fpaavg, int *orgavg) {
    *fpatmp = fpatmp_;
    *coretmp = coretmp_;
    *fpaavg = fpaavg_;
    *orgavg = orgavg_;
}
