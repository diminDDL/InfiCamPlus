#ifndef SIMPLE_PICTURE_PROCESSING_H
#define SIMPLE_PICTURE_PROCESSING_H



#include<stdio.h>
#include<stdlib.h>
#include <stdbool.h>
#ifdef __cplusplus
extern "C"  //C++
{
#endif
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
