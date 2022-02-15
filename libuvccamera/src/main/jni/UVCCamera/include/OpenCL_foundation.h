#ifndef OPENCL_FOUNDATION_H
#define OPENCL_FOUNDATION_H



#include<stdio.h>
#include<stdlib.h>
//#include<CL\cl.h>
#ifdef _APPLE_
#include <OpenCL/cl.h>
#else
#include <CL/cl.h>
#endif

#include"time_cal.h"
#include"OpenCL_phone.h"
typedef struct _GaussParms
{
    float nsigma;
    float alpha;
    float ema;
    float ema2;
    float b1;
    float b2;
    float a0;
    float a1;
    float a2;
    float a3;
    float coefp;
    float coefn;
} GaussParms, *pGaussParms;

class OpenCL_foundation {
private:
        cl_device_id device;
        cl_context context;
        cl_int err;
        cl_command_queue comqueue;
        cl_mem dSrcHold,dSrcOut,dSrc;
        cl_mem OtherOutput;
        cl_mem canshu01;
        cl_mem Final8bitOutput;
        cl_mem canshu02;
        cl_mem gsTemp1,gsTemp2,gsTemp3,canshu03,canshu04,ClPalette1,ClPalette2;
        cl_mem dDst;
        cl_kernel kernel;
        cl_program program;
        cl_event gpuExecution,gpuExecution2,gpuExecution3,gpuExecution4,gpuExecution5,gpuExecution6,gpuExecution20;
        cl_event datacopy[2];
        int swapBufferIndex;
        int buffersize;
        int ImageWidth;
        int ImageHeight;
        int kindOfPalette;
        // const char* cl[] ;
        size_t blockSize;
        size_t globalThread5;
        size_t localThread5;

        size_t localThreadsT[2] ;
        size_t globalThreadsT[2] ;

        size_t globalThreads6;
        size_t localThreads6;
        cl_uchar *dSrcHostHoldPtr;
        cl_uchar *dSrcHostOutPtr;
        // const char* cl[] ;
        int mCurrentAndroidVersion;
        unsigned short size1;
        int temp01,temp02;
        float temp03;
        unsigned short temp4;
        unsigned int size2;//16384/(256*4)
        unsigned int elements;//16384/(256*4)
        unsigned short range;
        unsigned short temp5;
        unsigned int temp6;
        unsigned int temp7;
        bool btemp1;
        bool btemp2;
        float fSigma;
        int iOrder;
        unsigned char PaletteRainbow[224*3];
        unsigned char PaletteHighRainbow[448*3];
        GaussParms oclGP;
        cl_kernel kernelRecursiveGaussian,kernelLinearPlat;
        short *rtemp01,*rtemp02;
        unsigned char *EightBitOutput;
        unsigned char *result;
        unsigned int *midResult;
        unsigned int *atemp01;
        unsigned int *atemp02;
        int htemp01,htemp02;//0-1000
        int ltemp01,ltemp02;//0-1000
        int otemp01,otemp02;//0-20
        float stemp,stemp2;//0-10
        unsigned short *input;
        cl_mem gsoutputImageBuffer;
        void computeGaussParms(float fSigma, int iOrder, GaussParms* pGP);
        bool status;
        float lptemp01,lptemp02;

        int dttemp01,dttemp02,dttemp03,dttemp04;


public:
	OpenCL_foundation(int currentAndroidVersion,int w,int h);
	~OpenCL_foundation();
size_t shrRoundUp(int group_size, int global_size);
void checkErr(cl_int err,int num);
char* readFile(const char* filename, size_t* length);
int OpenCL_InitDev(cl_device_id *device);
int OpenCL_InitSoft( int width,int height,int ksize,float sigma_d,
                float sigma_r,float alpha,int formatCoe);
 void OpenCL_Compute(unsigned short *Src,unsigned char* Dst,int kindOfPalette);
 void SetUserPalette(uint8_t* palette,int typeOfPalette);
 void changePalette(int typeOfPalette);
 void OpenCL_Release();
 void mtcan01(int imtcan01);
 void mtcan02(int imtcan02);
 void mtcan03(int imtcan03);
 void mtcan04(int imtcan04);
 void mtcan05(float imtcan05);
 void mtcan06(float imtcan06);
 void mtcan07(int imtcan07);
 void mtcan08(int imtcan08);


 int gettemp01();
 int gettemp02();
 int gettemp03();
 int gettemp04();
 float gettemp05();
 float gettemp06();
 int gettemp07();
 int gettemp08();

};


#endif