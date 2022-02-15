#ifndef SIMPLE_PICTURE_PROCESSING_H
#define SIMPLE_PICTURE_PROCESSING_H



#include<stdio.h>
#include<stdlib.h>
#include <stdbool.h>
#ifdef __cplusplus
extern "C"  //C++
{
#endif
    void SimplePictureProcessingInit(int width,int height);
    void SimplePictureProcessingInitMidVar(size_t** midVar);
    void SetParameter(float a,float b,float c,float d,float e,float f);//设置参数，参数已经调校好，请勿修改

	 /**
	 * 使用专业图像算法将8004数据转变为图像
	 * @para input：输入指针，指向8004数据开始
	 * @para output：输出指针，指向rgba图像开始
	 * @para kindOfPalette1： 0：白热。1：黑热。2：铁虹。 3：高对比彩虹。 4、彩虹2. >=6、用户色板
	 */
    void Compute(unsigned short* input,unsigned char* output,int kindOfPalette1,size_t** midVar);

	 /**
	 * 设置用户色板
	 * @para palette：输入指针，指向用户色板开始
	 * @para typeOfPalette：需要>=6
	 */
    void SetUserPalette(unsigned char* palette,int typeOfPalette);
    void SimplePictureProcessingDeinit();
    void SimplePictureProcessingDeinitMidVar(size_t** midVar);

	/**
	 * 获得色板
	 * @para type：
	 * (0)：256*3 铁虹
	 * (1)：256*3 彩虹1
	 * (2)：224*3 彩虹2
	 * (3)：448*3 高动态彩虹
	 * (4)：448*3 高对比彩虹
	 */
    const unsigned char* getPalette(int type);


#ifdef __cplusplus
}
#endif

#endif
