#include "thermometry2.h"
#include <stdint.h>
#include <math.h>

typedef uint16_t ushort;
typedef uint32_t uint;
typedef uint64_t ulong;
typedef float float10;

#define SQRT sqrt

float10 GetTempEvn(float Ttot, float dividend, float divisor) {
    double dVar1;

    dVar1 = pow((double)(Ttot + 273.15), 4.0);
    dVar1 = pow((double)(((float)dVar1 - dividend) * divisor), 0.25);
    return (float10)((float)dVar1 - 273.15);
}

// this is like Thermometry::sub_10001010() or ::tobj()
void CalcFixRaw(float *wvc, float *atmp, float *divisor, float t_atmosphere, float humidity,
                float distance, float emiss, float t_refl, float *dividend) {
    float atm;
    double dVar2;
    double dVar3;

    // NOTE apparently matches wvc() exactly, so this is done and dusted
    dVar2 = exp((double)(t_atmosphere * 6.8455e-07 * t_atmosphere * t_atmosphere +
                         ((t_atmosphere * 0.06939 + 1.5587) - t_atmosphere * 0.00027816 * t_atmosphere)));
    *wvc = (float)((double)humidity * dVar2);

    // NOTE this matches my atmt() exactly, done figured out
    dVar2 = exp((double)((SQRT(*wvc) * -0.002276 + 0.006569) *
                         (float)((uint)SQRT(distance) ^ 0x80000000)));
    dVar3 = exp((double)((float)((uint)SQRT(distance) ^ 0x80000000) *
                         (SQRT(*wvc) * -0.00667 + 0.01262)));
    atm = (float)(dVar3 * -0.8999999761581421 + dVar2 * 1.899999976158142);
    *atmp = atm;

    *divisor = 1.0 / (atm * emiss); // this is reciprocal of "divisor" in tobj()
    dVar2 = pow((double)(t_refl + 273.15), 4.0);
    //atm = *atmp; // no-op
    dVar3 = pow((double)(t_atmosphere + 273.15), 4.0);
    *dividend = (float)dVar3 * (1.0 - *atmp) + (1.0 - emiss) * (float)dVar2 * atm; // divident as in tobj()
    return;
}

void InitTempParam(float *param_1,float *param_2,float param_3,float param_4) {
    *param_1 = param_4 / (param_3 + param_3);
    *param_2 = (param_4 * param_4) / (param_3 * param_3 * 4.0);
    return;
}

int GetFix(float fpatemp, int rangemode, int width) {
    int iVar1;

    iVar1 = 0;
    if ((rangemode == 0x78) && (iVar1 = 0xaa, width != 0x100)) {
        iVar1 = (int)(390.0 - fpatemp * 7.05);
        if ((short)iVar1 < 0) {
            iVar1 = 0;
        }
        return iVar1;
    }
    return iVar1;
}

/* Maybe it is like this instead (from aarch64 decompile):
int GetFix(undefined4 param_1,int param_2,int param_3)
{
  short sVar1;
  float fVar2;

  if (param_2 != 0x78) {
    return 0;
  }
  if (param_3 != 0x100) {
    fVar2 = (float)NEON_fmsub(param_1,0x40e1999a,0x43c30000);
    sVar1 = (short)(int)fVar2;
    if (sVar1 < 0) {
      sVar1 = 0;
    }
    return (int)sVar1;
  }
  return 0xaa;
}

 */

void thermometrySearch2(int width, int height, float *temperatureTable, ushort *orgData, float *temperatureData,
                       int rangeMode, int outputMode)
{
    ushort *puVar1;
    float fVar2;
    float fVar3;
    float fVar4;
    float fVar5;
    float fVar6;
    ushort uVar7;
    ushort uVar8;
    ushort uVar9;
    ushort uVar10;
    float fVar11;
    float *pfVar12;
    int iVar13;
    int iVar14;
    uint uVar15;

    iVar13 = (height + -4) * width;
    uVar7 = orgData[iVar13 + 2];
    uVar8 = orgData[iVar13 + 3];
    uVar9 = orgData[iVar13 + 5];
    uVar10 = orgData[iVar13 + 6];
    if (width == 0x100) {
        iVar14 = (height + -3) * 0x100;
        goto LAB_000123fb;
    }
    if (width < 0x101) {
        if (width == 0xf0) {
            iVar14 = (height + -3) * 0xf0;
            goto LAB_000123fb;
        }
    }
    else {
        if (width == 0x180) {
            iVar14 = (height * 3 + -3) * 0x80;
            goto LAB_000123fb;
        }
        iVar14 = (height * 5 + -5) * 0x80;
        if (width == 0x280) goto LAB_000123fb;
    }
    iVar14 = iVar13 + 0xf;
    LAB_000123fb:
    fVar11 = *(float *)(orgData + iVar14 + 0x7f);
    if (((((orgData[iVar13 + 0xc] < 0x4000) && (orgData[iVar13 + 4] < 0x4000)) &&
          (orgData[iVar13 + 7] < 0x4000)) &&
         ((orgData[iVar13 + 0xd] < 0x4000 && (orgData[iVar13 + 0xe] < 0x4000)))) &&
        (orgData[iVar13 + 8] < 0x4000)) {
        fVar2 = *(float *)(temperatureTable + (uint)orgData[iVar13 + 4] * 4);
        fVar3 = *(float *)(temperatureTable + (uint)orgData[iVar13 + 0xd] * 4);
        fVar4 = *(float *)(temperatureTable + (uint)orgData[iVar13 + 7] * 4);
        fVar5 = *(float *)(temperatureTable + (uint)orgData[iVar13 + 0xe] * 4);
        fVar6 = *(float *)(temperatureTable + (uint)orgData[iVar13 + 8] * 4);
        *temperatureData = *(float *)(temperatureTable + (uint)orgData[iVar13 + 0xc] * 4) + fVar11;
        temperatureData[3] = fVar2 + fVar11;
        temperatureData[1] = (float)(uint)uVar7;
        temperatureData[2] = (float)(uint)uVar8;
        temperatureData[6] = fVar4 + fVar11;
        temperatureData[7] = fVar11 + fVar3;
        temperatureData[8] = fVar5 + fVar11;
        temperatureData[9] = fVar6 + fVar11;
        temperatureData[4] = (float)(uint)uVar9;
        temperatureData[5] = (float)(uint)uVar10;
        if ((outputMode == 4) && (0 < iVar13)) {
            uVar15 = (uint)*orgData;
            if (uVar15 < 0x4000) {
                puVar1 = orgData + iVar13;
                pfVar12 = temperatureData + 10;
                do {
                    orgData = orgData + 1;
                    *pfVar12 = *(float *)(temperatureTable + uVar15 * 4) + fVar11;
                    if (orgData == puVar1) {
                        return;
                    }
                    uVar15 = (uint)*orgData;
                    pfVar12 = pfVar12 + 1;
                } while (uVar15 < 0x4000);
            }
            puts("thermometrySearch err : 16383");
        }
        return;
    }
    puts("thermometrySearch err data");
    return;
}

void thermometryT4Line2(int width,int height,float *temperatureTable,ushort *fourLinePara,
                       float *floatFpaTmp,float *correction,float *Refltmp,float *Airtmp,float *humi,
                       float *emiss,ushort *distance,int cameraLens,float shutterFix,int rangeMode)
{
    float *pfVar1;
    ushort uVar2;
    ushort uVar3;
    int iter_tt_a;
    uint multivar1;
    int iVar4;
    int iter_tt_b;
    int iVar5;
    float fVar6;
    int in_GS_OFFSET;
    float10 Ttot;
    float shutterTemp;
    float fVar8;
    int local_a0;
    float local_a0;
    float distance2;
    int local_7c;
    float local_7c;
    int local_74;
    float local_74f;
    int off_correction;
    int off_refltmp;
    int off_airtmp;
    int off_humi;
    int off_emiss;
    int off_distance;
    int local_48;
    int local_40;
    float wvc;
    float atmp;
    float ldivisor;
    float local_2c;
    float local_28;
    float ldividend;
    int local_20;
    int uStack20_problyunused;
    ushort distance3;
    float fpatemp2;

    uStack20_problyunused = 0x115a9;
    wvc = 0.0;
    atmp = 0.0;
    ldivisor = 0.0;
    local_2c = 0.0;
    local_28 = 0.0;
    ldividend = 0.0;
    local_20 = *(int *)(in_GS_OFFSET + 0x14);
    multivar1 = (uint)fourLinePara[1];
    if (width == 256) {
        iter_tt_b = 0x20a;
        iVar5 = 0x202; // shutterTemp
        iVar4 = 0x200; // cal_00
        local_48 = 0x100;
        off_distance = 0x312;
        off_emiss = 0x30e;
        off_humi = 0x30a;
        off_airtmp = 0x306;
        off_refltmp = 0x302;
        off_correction = 0x2fe;
        local_74 = 0x216;
        local_7c = 0x212;
        local_a0 = 0x20e;
        local_40 = 0x244;
        *floatFpaTmp = 20.0 - (float)(multivar1 - 8617) / 37.682;
        iter_tt_a = 0x206;
        goto LAB_00011745;
    }
    if (width < 257) {
        if (width == 240) {
            iter_tt_b = 0x1ea;
            iVar5 = 0x1e2;
            iVar4 = 0x1e0;
            local_48 = 0xf0;
            off_distance = 0x2f2;
            off_emiss = 0x2ee;
            off_humi = 0x2ea;
            off_airtmp = 0x2e6;
            off_refltmp = 0x2e2;
            off_correction = 0x2de;
            local_74 = 0x1f6;
            local_7c = 0x1f2;
            local_a0 = 0x1ee;
            local_40 = 0x234;
            *floatFpaTmp = 20.0 - (float)(multivar1 - 7800) / 36.0;
            iter_tt_a = 0x1e6;
            goto LAB_00011745;
        }
    }
    else {
        if (width == 384) {
            iter_tt_b = 0x90a;
            iVar5 = 0x902;
            iVar4 = 0x900;
            off_distance = 0xa12;
            off_emiss = 0xa0e;
            off_humi = 0xa0a;
            off_airtmp = 0xa06;
            off_refltmp = 0xa02;
            off_correction = 0x9fe;
            local_74 = 0x916;
            local_7c = 0x912;
            local_a0 = 0x90e;
            local_40 = 0x5c4;
            local_48 = 0x480;
            *floatFpaTmp = 20.0 - (float)(multivar1 - 7800) / 36.0;
            iter_tt_a = 0x906;
            goto LAB_00011745;
        }
        if (width == 640) {
            iter_tt_b = 0xf0a;
            iVar5 = 0xf02;
            iVar4 = 0xf00;
            off_distance = 0x1012;
            off_emiss = 0x100e;
            off_humi = 0x100a;
            off_airtmp = 0x1006;
            off_refltmp = 0x1002;
            off_correction = 0xffe;
            local_74 = 0xf16;
            local_7c = 0xf12;
            local_a0 = 0xf0e;
            local_40 = 0x8c4;
            local_48 = 0x780;
            *floatFpaTmp = 20.0 - (float)(multivar1 - 6867) / 33.8;
            iter_tt_a = 0xf06;
            goto LAB_00011745;
        }
    }
    off_distance = 0x112;
    iter_tt_b = 10;
    off_emiss = 0x10e;
    iter_tt_a = 6;
    off_humi = 0x10a;
    iVar5 = 2;
    off_airtmp = 0x106;
    iVar4 = 0;
    off_refltmp = 0x102;
    off_correction = 0xfe;
    local_74 = 0x16;
    local_7c = 0x12;
    local_a0 = 0xe;
    local_40 = 0x144;
    local_48 = 0;
    LAB_00011745:
    uVar2 = *(ushort *)(fourLinePara + iVar5);
    uVar3 = *(ushort *)(fourLinePara + iVar4);
    fVar8 = (float) *(ushort *)(fourLinePara + iter_tt_b);
    fVar6 = (float) *(ushort *)(fourLinePara + (int)local_a0);
    pfVar1 = (float *)(fourLinePara + (int)local_74); // cal_05
    local_74f = *(uint16_t *)(fourLinePara + (int)local_7c); // cal_04
    local_a0 = *(uint16_t *)(fourLinePara + iter_tt_a);
    local_7c = (float) *(ushort *) pfVar1; // cal_05
    if ((width == 640) && (rangeMode == 400)) { // TODO do not forget to implement this
        local_74f = *(float *)(fourLinePara + local_48 + 0x14a);
        fVar8 = *(float *)(fourLinePara + local_48 + 0x146);
        fVar6 = *(float *)(fourLinePara + local_48 + 0x148);
        local_a0 = *(float *)(fourLinePara + local_40);
        local_7c = *(float *)(fourLinePara + local_48 + 0x14c);
    }
    *correction = *(float *)(fourLinePara + off_correction);
    *Refltmp = *(float *)(fourLinePara + off_refltmp);
    *Airtmp = *(float *)(fourLinePara + off_airtmp);
    *humi = *(float *)(fourLinePara + off_humi);
    *emiss = *(float *)(fourLinePara + off_emiss);
    distance3 = *(ushort *)(fourLinePara + off_distance);
    *distance = distance3;
    if (cameraLens == 68) {
        multivar1 = (uint)*distance * 3;
    } else {
        if (cameraLens != 130) {
            distance3 = *distance;
        }
        multivar1 = (uint)distance3;
    }
    distance2 = (float)multivar1;
    InitTempParam(&local_2c, &local_28, local_a0, fVar8); // local_a0 and fVar8 come from frame somewhere, what are they?

    //CalcFixRaw(&wvc,&atmp,&ldivisor,*Airtmp,*humi,distance2,*emiss,*Refltmp,&ldividend);
    // TODO manual parameters for test
    CalcFixRaw(&wvc, &atmp, &ldivisor, 21.0, 0.45, 1, 0.95, 21.0, &ldividend);

    shutterTemp = ((float)(uint)uVar2 / 10.0 - 273.15) + shutterFix;
    fpatemp2 = *floatFpaTmp;
    iter_tt_a = GetFix(fpatemp2,rangeMode,width);
    multivar1 = (uint)uVar3 - iter_tt_a & 0xffff;
    fVar8 = local_a0 * shutterTemp * shutterTemp + shutterTemp * fVar8;
    local_7c = fVar6 * fpatemp2 * fpatemp2 + local_74f * fpatemp2 + local_7c; // local_74f == cal_04, local_7c = cal_05
    if (cameraLens == 68) {
        iter_tt_a = -multivar1;
        if (distance2 < 60.0) {
            do {
                Ttot = (float10)GetTempEvn(SQRT(((float)iter_tt_a * local_7c + fVar8) / local_a0 + local_28)
                                           - local_2c, ldividend, ldivisor);
                temperatureTable[multivar1 + iter_tt_a] =
                        (((float)Ttot - *Airtmp) * (distance2 * 0.85 + 1.125)) / 100.0 + (float)Ttot;
                iter_tt_a = iter_tt_a + 1;
            } while (iter_tt_a != 0x4000 - multivar1);
        }
        else {
            do {
                Ttot = (float10)GetTempEvn(SQRT(((float)iter_tt_a * local_7c + fVar8) / local_a0 + local_28)
                                           - local_2c, ldividend, ldivisor);
                temperatureTable[multivar1 + iter_tt_a] =
                        (((float)Ttot - *Airtmp) * 52.125) / 100.0 + (float)Ttot;
                iter_tt_a = iter_tt_a + 1;
            } while (iter_tt_a != 0x4000 - multivar1);
        }
    }
    else {
        iter_tt_b = -multivar1;
        iter_tt_a = 0x4000 - multivar1;
        if (cameraLens == 130) {
            if (distance2 < 20.0) {
                do {
                    Ttot = (float10)GetTempEvn(SQRT(((float)iter_tt_b * local_7c + fVar8) / local_a0 +
                                                    local_28) - local_2c, ldividend, ldivisor);
                    temperatureTable[multivar1 + iter_tt_b] =
                            (((float)Ttot - *Airtmp) * (distance2 * 0.85 - 1.125)) / 100.0 + (float)Ttot;
                    iter_tt_b = iter_tt_b + 1;
                } while (iter_tt_b != iter_tt_a);
            }
            else {
                do {
                    Ttot = (float10)GetTempEvn(SQRT(((float)iter_tt_b * local_7c + fVar8) / local_a0 +
                                                    local_28) - local_2c, ldividend, ldivisor);
                    temperatureTable[multivar1 + iter_tt_b] =
                            (((float)Ttot - *Airtmp) * 15.875) / 100.0 + (float)Ttot;
                    iter_tt_b = iter_tt_b + 1;
                } while (iter_tt_b != iter_tt_a);
            }
        }
        else if (distance2 < 20.0) {
            do {
                Ttot = (float10)GetTempEvn(SQRT(((float)iter_tt_b * local_7c + fVar8) / local_a0 + local_28)
                                           - local_2c, ldividend, ldivisor);
                temperatureTable[multivar1 + iter_tt_b] =
                        (((float)Ttot - *Airtmp) * (distance2 * 0.85 - 1.125)) / 100.0 + (float)Ttot;
                iter_tt_b = iter_tt_b + 1;
            } while (iter_tt_b != iter_tt_a);
        }
        else {
            do {
                Ttot = (float10)GetTempEvn(SQRT(((float)iter_tt_b * local_7c + fVar8) / local_a0 + local_28)
                                           - local_2c, ldividend, ldivisor);
                temperatureTable[multivar1 + iter_tt_b] =
                        (((float)Ttot - *Airtmp) * 15.875) / 100.0 + (float)Ttot;
                iter_tt_b = iter_tt_b + 1;
            } while (iter_tt_b != iter_tt_a);
        }
    }
    /*if (local_20 != *(int *)(in_GS_OFFSET + 0x14)) {
        //FUN_000105f0();
        thermometrySearch();
        return;
    }*/
    return;
}
