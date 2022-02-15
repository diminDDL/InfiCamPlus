
#ifndef THERMOMETRY_H_
#define THERMOMETRY_H_
#define DEBUG 0
#include <stdbool.h>
#ifdef __cplusplus
extern "C"  //C++
{
#endif
/**
* 计算温度对应表，用camera数据首地址
* @para width:宽
* @para height:高
* @para temperatureTable:输出，调用完此函数后，温度对应表会被填充
* @para orgData :输入，camera数据首地址
* @para floatFpaTmp: 输出，fpa温度
* @para correction :输出，温度整体修正值
* @para Refltmp :输出，发射温度
* @para Airtmp :输出，环境温度
* @para humi :输出，湿度
* @para emiss :输出，发射率
* @para distance :输出，距离
* @para cameraLens :设置，镜头大小:目前支持两种，68：使用6.8mm镜头，130：使用13mm镜头,默认130。
* @para shutterFix :设置，快门校正，一般为0.
* @para rangeMode :设置，测温范围：120：温度范围为-20-120摄氏度。400：温度范围为-20-400摄氏度,另外人体测温产品T3H也使用这种模式
*/
void thermometryT(int width,
                  int height,
                  float *temperatureTable,
                  unsigned short *orgData,
                  float* floatFpaTmp,
                  float* correction,
                  float* Refltmp,
                  float* Airtmp,
                  float* humi,
                  float* emiss,
                  unsigned short* distance,
                  int cameraLens,
                  float shutterFix,
                  int rangeMode);

  /**
  * 计算温度对应表，用camera数据后四行参数
  * @para width:宽
  * @para height:高
  * @para temperatureTable:输出，调用完此函数后，温度对应表会被填充
  * @para orgData :输入，camera数据首地址
  * @para floatFpaTmp: 输出，fpa温度
  * @para correction :输出，温度整体修正值
  * @para Refltmp :输出，发射温度
  * @para Airtmp :输出，环境温度
  * @para humi :输出，湿度
  * @para emiss :输出，发射率
  * @para distance :输出，距离
  * @para cameraLens :设置，镜头大小:目前支持两种，68：使用6.8mm镜头，130：使用13mm镜头,默认130。
  * @para shutterFix :设置，快门校正，一般为0.
  * @para rangeMode :设置，测温范围：120：温度范围为-20-120摄氏度。400：温度范围为-20-400摄氏度,另外人体测温产品T3H也使用这种模式
  */

void thermometryT4Line(int width,
                       int height,
                       float *temperatureTable,
                       unsigned short *fourLinePara,
                       float* floatFpaTmp,
                       float* correction,
                       float* Refltmp,
                       float* Airtmp,
                       float* humi,
                       float* emiss,
                       unsigned short* distance,
                       int cameraLens,
                       float shutterFix,
                       int rangeMode);

/**
  * 使用温度对应表来查询温度
  * @para width:宽
  * @para height:高
  * @para temperatureTable:输入温度对应表，用于查询对应温度
  * @para orgData :输入camera数据，用于查询对应温度
  * @para temperatureData: 输出，根据8004或者8005模式来查表，8005模式下仅输出以下10个参数，8004模式下数据以下参数+全局温度数据
  *          temperatureData[0]=centerTmp;
  *          temperatureData[1]=(float)maxx1;
  *          temperatureData[2]=(float)maxy1;
  *          temperatureData[3]=maxTmp;
  *          temperatureData[4]=(float)minx1;
  *          temperatureData[5]=(float)miny1;
  *          temperatureData[6]=minTmp;
  *          temperatureData[7]=point1Tmp;
  *          temperatureData[8]=point2Tmp;
  *          temperatureData[9]=point3Tmp;
  * @para rangeMode :设置，测温范围：120：温度范围为-20-120摄氏度。400：温度范围为-20-400摄氏度,另外人体测温产品T3H也使用这种模式
  * @para outputMode：4：8004模式。5：8005模式
  */
void thermometrySearch(            int width,
                                   int height,
                                   float *temperatureTable,
                                   unsigned short *orgData,
                                   float* temperatureData,
                                   int rangeMode,
                                   int outputMode);

/**
 * 使用温度对应表来查询个别温度
 * @para width:宽
 * @para height:高
 * @para temperatureTable:输入，温度对应表，用于查询对应温度
 * @para fourLinePara :输入，后四行参数
 * @para count :设置，要查询的个数
 * @para queryData :输入，要查询的8004模式下的数据，连续的，符合查询个数
 * @para temperatureData: 输出，要查询的数据的温度
 * @para rangeMode :设置，测温范围：120：温度范围为-20-120摄氏度。400：温度范围为-20-400摄氏度,另外人体测温产品T3H也使用这种模式
 */
void thermometrySearchSingle(      int width,
                                   int height,
                                   float* temperatureTable,
                                   unsigned short* fourLinePara,
                                   int count,
                                   unsigned short* queryData,
                                   float* temperatureData,
                                   int rangeMode);
/**
  * 使用温度对应表来查询温度
  * @para width:宽
  * @para height:高
  * @para temperatureTable:输入温度对应表，用于查询对应温度
  * @para fourLinePara :输入，后四行参数
  * @para temperatureData: 输出，仅输出以下10个参数
  *          temperatureData[0]=centerTmp;
  *          temperatureData[1]=(float)maxx1;
  *          temperatureData[2]=(float)maxy1;
  *          temperatureData[3]=maxTmp;
  *          temperatureData[4]=(float)minx1;
  *          temperatureData[5]=(float)miny1;
  *          temperatureData[6]=minTmp;
  *          temperatureData[7]=point1Tmp;
  *          temperatureData[8]=point2Tmp;
  *          temperatureData[9]=point3Tmp;
  * @para rangeMode :设置，测温范围：120：温度范围为-20-120摄氏度。400：温度范围为-20-400摄氏度,另外人体测温产品T3H也使用这种模式
  */

void thermometrySearchCMM(         int width,
                                   int height,
                                   float* temperatureTable,
                                   unsigned short* fourLinePara,
                                   float* temperatureData,
                                   int rangeMode);
/**
  * 用于同一场景不同距离的温度修正：比如整体设置距离3m，但是某点位距离5m，那么把该点的温度提取出来，输入为inputTemp，距离5输入为distance
  * @para inputTemp:输入温度
  * @para distance:距离
  * @para Airtmp:输入，环境温度
  * @para cameraLens镜头大小:目前支持两种，68：使用6.8mm镜头，130：使用13mm镜头,默认130。
  */
float distanceFix(float inputTemp,float distance,float Airtmp,int cameraLens);

float thermFix (unsigned short* temp_in_t, int in_count, unsigned short* temp_out_t,
                    float temp_atm, float temp_ref, float emiss,
                    float trans, unsigned short distance);


#ifdef __cplusplus
}
#endif

#endif

