#include "InfiFrame.h"

#include "CameraSettings.h"
#include "Utils.h"
#include "InfiCam.h"

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <sys/types.h>


InfiFrame::InfiFrame(CameraSettings & p_cam_settings, const int stream_width, const int stream_height):
    cam_settings(p_cam_settings), width(stream_width), stream_height(stream_height), vision_height(stream_height-METADATA_HEIGHT) {

    gain_k_buffer = new uint16_t[width*vision_height];
    offset_b_buffer_accu = new uint32_t[width*vision_height];
    offset_b_buffer = new uint16_t[width*vision_height];
}

InfiFrame::~InfiFrame(){
    delete gain_k_buffer;
    gain_k_buffer = nullptr;
    delete offset_b_buffer_accu;
    offset_b_buffer_accu = nullptr;
    delete offset_b_buffer;
    offset_b_buffer = nullptr;
}



void InfiFrame::start_calibration(){
    memset(offset_b_buffer_accu,0,width*vision_height*sizeof(uint32_t));
    offset_b_buffer_counter = 0;
}



int InfiFrame::rangeToDeviceRange(const CameraTemperatureRange t) {
    switch (t) {
        case CameraTemperatureRange::RANGE_N20_120:   return 120;
        case CameraTemperatureRange::RANGE_120_400:   return 400; //T2S is 400 NOT 450, even if the UI says 450
        default:  __android_log_assert(nullptr, LOG_TAG,"Who wrote this?");

    }
}


void InfiFrame::updateTable(const uint16_t * frame){
    float floatFpaTmp;
    float temperature_correction = cam_settings.temperature_correction;
    float reflection_temperature = cam_settings.reflection_temperature;
    float air_temperature = cam_settings.air_temperature;
    float humidity = cam_settings.humidity;
    float emissivity = cam_settings.emissivity;
    uint16_t distance = cam_settings.distance;
    //technically, this function could update the cam_settings, but it's already done by the time this is called
    thermometryT4Line(width,
                        stream_height,
                        temperature_table,
                        frame+(width*vision_height),
                        &floatFpaTmp,
                        &temperature_correction,
                        &reflection_temperature,
                        &air_temperature,
                        &humidity,
                        &emissivity,
                        &distance,
                        cam_settings.lens,
                        0.0f, // "shutter_fix", must be set manually but never used by the app, exists only for non-raw devices.
                        InfiFrame::rangeToDeviceRange(cam_settings.temperature_range),
                        cam_settings.use_raw_logic,
                        false);
    cam_settings.max_temperature_clipping = temperature_table[0x3FFF];
}

void InfiFrame::calibrate_on_frame(const uint16_t * frame){
    for(int i = 0 ; i < width*vision_height ; i++){
        offset_b_buffer_accu[i] += frame[i];
    }
    offset_b_buffer_counter++;
    updateTable(frame);
}



void InfiFrame::end_calibration(){
    for(int i = 0 ; i < width*vision_height ; i++){
        offset_b_buffer[i] = (uint32_t)round((float)offset_b_buffer_accu[i]/(float)offset_b_buffer_counter);
    }
    destripe(offset_b_buffer);
}

void InfiFrame::frame_to_temp(const uint16_t * frame, float * temp) const {
    for (size_t i = 0; i < width*vision_height; ++i) {
        if(frame[i] > 0x3FFF){__android_log_assert(nullptr, nullptr,"Big image processing bug.");}
        temp[i] = temperature_table[frame[i]];
    }


}

void InfiFrame::apply_calibration(const uint16_t * source_frame, uint16_t * calibrated_frame){
    //apply kb gain

    //TODO: Confirm 2000 and 6000 are correct in every conditions. I had to guess which to use when. My reverse engineering couldn't find where it is set.
    int pedestal;
    switch(InfiFrame::rangeToDeviceRange(cam_settings.temperature_range)){
        case 120:
            pedestal = 6000; break;
        case 400:
            pedestal = 2000; break;
        default:
            __android_log_assert(nullptr, nullptr,"Temperature range not supported");
    }
    for(int i = 0 ; i < width*vision_height ; i++){
        calibrated_frame[i] = (uint16_t)std::clamp(pedestal + ((int)source_frame[i] - (int)offset_b_buffer[i]) *(int)gain_k_buffer[i] /(1<<14),0,0x3fff);
    }
}


void InfiFrame::updateSettings(const uint16_t *fourLinePara){
        int offset_cal = 0;
        char model_string[16];
        if (width < 0x180) {
            if (width == 0xf0) {
                offset_cal = 0xf0;
            }
            else if (width == 0x100) {
                offset_cal = 0x100;
            }
        }
        else if (width == 0x180) {
            float _;
            ComparePN(0x180,fourLinePara,&_,model_string);
            offset_cal = 0x480;
        }
        else if (width == 0x280) {
            offset_cal = 0x780;
        }
        cam_settings.temperature_correction = *(float*)(fourLinePara + (offset_cal + 0x7f));
        cam_settings.reflection_temperature = *(float*)(fourLinePara + (offset_cal + 0x81));
        cam_settings.air_temperature = *(float*)(fourLinePara + (offset_cal + 0x83));
        cam_settings.humidity = *(float*)(fourLinePara + (offset_cal + 0x85));
        cam_settings.emissivity = *(float*)(fourLinePara + (offset_cal + 0x87));
        cam_settings.distance = fourLinePara[offset_cal + 0x89];
    }

void InfiFrame::thermometryT4Line(int width, int height,
                                  float *temperatureTable, //output, post-nuc sensor value to temperature table
                                  const uint16_t *fourLinePara, //input, pointer to last 4 lines of the frame
                                  float *floatFpaTmp, //output, sensor or shutter temp (not sure)
                                  float *correction, //input and output, temperature correction in C
                                  float *Refltmp, //input and output
                                  float *Airtmp, //input and output
                                  float *humi, //input and output
                                  float *emiss, //input and output
                                  uint16_t *distance, //input and output
                                  int cameraLens, //input, camera lens in .1mm
                                  float shutterFix, //input, unused in RAW sensors.
                                  int rangeMode,//input, 120 or 400
                                  int isNewProduct,//input, wherever to use the non-raw or raw codepath
                                  int isOffline//input, a third codepath, not sure what for
                                )

{
    LOGD("Entering thermometryT4Line\n");

    int offset_cal = 0;


    uint16_t fpaTemp = fourLinePara[isOffline?2:1];
    char model_string[16];
    if (width < 0x180) {
        if (width == 0xf0) {
            *floatFpaTmp = (float)(fpaTemp - 0x1e78) / -36.0f + 20.0f;
            offset_cal = 0xf0;
        }
        else if (width == 0x100) {
            *floatFpaTmp = (float)(fpaTemp - 0x21a9) / -37.682f + 20.0f;
            offset_cal = 0x100;
        }
    }
    else if (width == 0x180) {
        ComparePN(0x180,fourLinePara,floatFpaTmp,model_string);
        offset_cal = 0x480;
    }
    else if (width == 0x280) {
        offset_cal = 0x780;
        *floatFpaTmp = (float)(fpaTemp - 0x1ad3) / -33.8f + 20.0f;
    }
    LOGD("Device register specific code => offset_cal= %d, floatFpaTmp=%f\n",offset_cal,*floatFpaTmp);

    uint16_t ref_pixel = fourLinePara[offset_cal];
    uint16_t shut_temp = *(fourLinePara + (offset_cal | 1));
    float calibrate_correct = cameraLens==0x82?*(float *)(fourLinePara + (offset_cal | 0xd)):0.0f;
    uint16_t offset_a = offset_cal | 3;
    uint16_t offset_ka = offset_cal | 7;
    uint16_t offset_b = offset_cal | 5;
    uint16_t offset_kb = offset_cal | 9;
    uint16_t offset_kc = offset_cal | 0xb;
    if ((width == 0x280) && (rangeMode == 400)) {
        offset_a = offset_cal + 0x144;
        offset_b = offset_cal + 0x146;
        offset_ka = offset_cal + 0x148;
        offset_kb = offset_cal + 0x14a;
        offset_kc = offset_cal + 0x14c;
    }

    LOGD("ref_pixel=%d, shut_temp=%d\n",ref_pixel,shut_temp);
    LOGD("calibrate_correct=%f\n",calibrate_correct);
    LOGD("offset_a=%d offset_b=%d offset_ka=%d offset_ka=%d offset_ka=%d\n",offset_a,offset_b,offset_ka,offset_kb,offset_kc);


    float a = *(float *)(fourLinePara + offset_a);
    float b = *(float *)(fourLinePara + offset_b);
    float ka = *(float *)(fourLinePara + offset_ka);
    float kb = *(float *)(fourLinePara + offset_kb);
    float kc = *(float *)(fourLinePara + offset_kc);

    LOGD("a=%f b=%f ka=%f kb=%f kc=%f\n",a,b,ka,kb,kc);


    float float_shut_temp = (float)shut_temp / 10.0f + -273.15f;
    if(isNewProduct){
        float adjusted_shut_temper = (shut_temp < 0x801)?(float)shut_temp:(float)((uint16_t)0xfff - shut_temp);
        float shut_temper_multiplier = (shut_temp < 0x801)?0.625:-0.625;
        float_shut_temp = (adjusted_shut_temper * shut_temper_multiplier + 2731.5f) / 10.0f - 273.15f;
        LOGD("isNewProduct, float_shut_temp=%f\n",float_shut_temp);
    }

    if(!isNewProduct){ //these do not exist in raw sensors.
        *correction = *(float*)(fourLinePara + (offset_cal + 0x7f));
        *Refltmp = *(float*)(fourLinePara + (offset_cal + 0x81));
        *Airtmp = *(float*)(fourLinePara + (offset_cal + 0x83));
        *humi = *(float*)(fourLinePara + (offset_cal + 0x85));
        *emiss = *(float*)(fourLinePara + (offset_cal + 0x87));
        *distance = fourLinePara[offset_cal + 0x89];
    }

    LOGD("correction=%f Refltmp=%f Airtmp=%f humi=%f emiss=%f distance=%d\n",*correction,*Refltmp,*Airtmp,*humi,*emiss,*distance);


    float adjusted_distance = (cameraLens == 0x44)? (float)*distance * 3.0f : (float)*distance;
    float clamped_distance = (cameraLens == 0x44)?min(adjusted_distance, 60.0f):min(adjusted_distance, 20.0f);
    float distance_offset = (cameraLens == 0x44)?1.125: -1.125;



    float air_temp_cubed = expf(1.5587f + *Airtmp * 0.06939f + powf(*Airtmp,2) * -0.00027816f + powf(*Airtmp,3) * 6.8455e-07f);
    float atmo_trans_a = expf((sqrt(air_temp_cubed * *humi) * -0.002276f + 0.006569f) * -sqrt(adjusted_distance));
    float atmo_trans_b = expf((sqrt(air_temp_cubed * *humi) * -0.00667f + 0.01262f) * -sqrt(adjusted_distance));
    float atmo_trans = atmo_trans_a * 1.899999976158142f + atmo_trans_b * -0.8999999761581421f;
    float quad_fefl_temp = powf(*Refltmp + 273.15f,4.0f);
    float quad_air_temp = powf(*Airtmp + 273.15f,4.0f);

    float chosen_shutter_fix = isNewProduct? (float)*((int8_t*)fourLinePara + (4*width+0x5F)) / 10.0f : shutterFix; //fourLinePara is used as a int8_t here
    float fixedShutTemp = float_shut_temp + chosen_shutter_fix;

    LOGD("fixedShutTemp=%f\n",fixedShutTemp);

    int raw_offset_cal = 0.0f;
    if (rangeMode == 0x78) {
        raw_offset_cal = (float)0xaa;
        if (width != 0x100) {
            raw_offset_cal = max((int)(*floatFpaTmp * -7.05f + 390.0f),0);
        }
    }

    LOGD("raw_offset_cal=%d\n",raw_offset_cal);


    uint16_t adjusted_ref_pixel = ref_pixel - raw_offset_cal;
    float q_completethesquare = (b * b) / (a * a * 4.0f);
    float r_completethesquare = b / (a + a);
    float rad_factor = 1.0f / (*emiss * atmo_trans);
    float rad_offset = (1.0f - *emiss) * quad_fefl_temp * atmo_trans + (1.0f - atmo_trans) * quad_air_temp;
    float signal_shutter_temp = fixedShutTemp*fixedShutTemp * a + fixedShutTemp * b;
    float gain = kc + kb * *floatFpaTmp + ka * *floatFpaTmp * *floatFpaTmp;

    LOGD("adjusted_ref_pixel=%d gain=%f\n",adjusted_ref_pixel,gain);


    for(int i = 0 ; i <  0x4000 ; i++){
        float v =
                q_completethesquare +
                (signal_shutter_temp + gain * (float)(i - adjusted_ref_pixel) //= M(i)
                ) / a;
        temperatureTable[i] = -273.0;
        if (v < 0.0) { continue;} // pixel is reading below the lowest radiance the calibration model covers
        float t_raw = sqrt(v) - r_completethesquare; //temperature an ideal ε=1 emitter would have to produce this sensor reading, before any emissivity / environment / distance physics.
        if(powf(t_raw + 273.15f,4.0f) < rad_offset) { continue;} // the environmental radiance exceeds the total radiance actually measured

        float z1 = (powf(t_raw + 273.15f,4.0f) - rad_offset);
        float t_rad = powf(rad_factor * (powf(t_raw + 273.15f,4.0f) - rad_offset),0.25f) -273.15f;
        float dist_correction = ((clamped_distance * 0.85f + distance_offset) * (t_rad - *Airtmp)) / 100.0f;

        temperatureTable[i] = calibrate_correct + t_rad + dist_correction;

        if(std::isnan(temperatureTable[i])){
            __android_log_assert(nullptr, "libinficam","We got a nan %f at %d with v=%f t_raw=%f t_rad=%f, dist_correction=%f\n",temperatureTable[i],i,v,t_raw,t_raw,dist_correction);
        }
        if(i > 0 && temperatureTable[i-1] > temperatureTable[i]){ //temperature should increase when signal increases
            //TODO: Investigate if we need to send this to the app or not. The only way this can happens is a bug or an incompatible camera model.
            __android_log_assert(nullptr, LOG_TAG,
                                 "Bad sensor temperature table calculation, %f should be greater than %f",  temperatureTable[i], temperatureTable[i-1]);
        }
    }
}
void InfiFrame::ComparePN(const int width,
                          const uint16_t *fourLinePara,
                          float *floatFpaTmp,
                          char *output_version_string) {
    LOGD("Using ComparePN");
    int offset = 0;
    if(width == 0x180){
        offset = 0x950;
    } else if (width==0x280){
        offset = 0xf50;
    }

    uint16_t fpaTemp = fourLinePara[1];
    const char * version_string = (const char*)fourLinePara + offset;
    strncpy(output_version_string, version_string, 15);
    output_version_string[15] = '\0';

    LOGD("Version_string=\"%s\"\n",version_string);


    int otherModel = !strstr(output_version_string, "312") & !strstr(output_version_string, "DX300");
    int tempOffset = -0x1ad3;
    if (otherModel) {
        tempOffset = -0x1e78;
    }

    float factors[] = {36.0f,33.8f};
    *floatFpaTmp = 20.0f - (float)(tempOffset + fpaTemp) / factors[!otherModel];
}


void InfiFrame::destripe(uint16_t * frame) const{
bool use_fast = false;
#if defined(__aarch64__) //arm64 has neon
    destripe_neon(frame);
#elif defined(__arm__) //32 bit arm, neon support is optional
    static auto impl = Utils::has_neon()?&InfiFrame::destripe_neon:&InfiFrame::destripe_standard; //trick to call has_neon() only once
    (this->*impl)(frame);
#else
    destripe_standard(frame);
#endif
}

/*
 * Inspired by the original library.
 * Remove vertical stripes based on a moving window. Average the delta of this pixel vs horizontal neighbours for a whole column.
 * Ignore deltas that are too high, we are targeting solid areas of the same color.
 * Then add that average delta to the column.
 * Window is 8 left to 8 right. It mirrors back inside at the edge of the frame, and compares itself, for simplicity/speed.
 * Added vote cap instead of the two-level mean from the official version.
 */
void InfiFrame::destripe_standard(uint16_t * frame) const {
    int deltas[width];
    int counts[width];
    memset(deltas,0,width*sizeof(int));
    memset(counts,0,width*sizeof(int));

    int cc_div_table[8*2+1 +1];
    for (int i = 1; i <= 8*2+1 ; i++) cc_div_table[i] = ((1<<16) + i-1) / i; //fast 1/x lookup table, shifted up 16

    for(int y = 0 ; y < vision_height ; y++){
        for(int x = 0 ; x < width ; x++){
            int current_delta = 0;
            int current_count = 0;
            uint16_t center_pixel = frame[x+y*width];
            for(int d = -8 ; d <= 8 ; d++){
                int x_offset_d = (width-1)-abs((width-1)-abs(x+d)); //x+d but mirror around 0 and (width-1), without branches (abs is fast).
                int pixel_delta = frame[x_offset_d+y*width]-center_pixel;
                int is_low_enough = (abs(pixel_delta) <= DESTRIPE_CONTRAST_LIMIT); //contrast awareness, only take flat surfaces into account
                current_delta += pixel_delta*is_low_enough;
                current_count += is_low_enough;
            }
            int votes = min(current_count,DESTRIPE_VOTE_CAP+1);
            deltas[x] += (current_delta*votes*cc_div_table[current_count])>>16; //+1 because we self compare; 16 because cc_div_table
            counts[x] += votes; //+1 because we self compare
        }
    }

    int corr[width];
    for(int x = 0 ; x < width ; x++){
        corr[x] = (int)round(deltas[x]/counts[x]); //count is never zero because we self compare
    }

    for(int y = 0 ; y < vision_height ; y++){
        for(int x = 0 ; x < width ; x++){
            frame[x+y*width] = std::clamp(frame[x+y*width] + corr[x],0,0x3FFF); //count is never zero because we self compare
        }
    }
}

#if defined(__aarch64__) || defined(__arm__)
/*
 * This was mostly AI generated. I know very little about NEON.
 */
#include <arm_neon.h>

//__attribute__((target("fpu=neon")))
__attribute__((target("neon")))
void InfiFrame::destripe_neon(uint16_t* frame) const {
    int deltas[width];
    int counts[width];
    memset(deltas,0,width*sizeof(int));
    memset(counts,0,width*sizeof(int));

    const int DESTRIPE_VOTE_CAP_plus_1 = DESTRIPE_VOTE_CAP + 1;

    const int16x8_t constrast_limit_neon = vdupq_n_s16(DESTRIPE_CONTRAST_LIMIT);


    // --- scalar per-column accumulation (edges + fallback) ---
    auto scalar_col = [&](const uint16_t* row, int x) {
        int delta = 0;
        int count = 0;
        int center = row[x];
        for (int offset = -8; offset <= 8; offset++) {
            int x_local = (width - 1) - std::abs((width - 1) - std::abs(x + offset)); // mirror
            int delta_local = (int)row[x_local] - center;
            if (std::abs(delta_local) <= DESTRIPE_CONTRAST_LIMIT) {
                delta += delta_local;
                count += 1;
            }
        }
        int votes = std::min(count, DESTRIPE_VOTE_CAP_plus_1);
        deltas[x] += delta * votes / count;
        counts[x] += votes;
    };

    // --- neon per-column accumulation --- does 8 columns worth of the scalar version at a time
    auto neon_col = [&](const uint16_t* row, int x) {
        //delta needs to be int32 so we need two.
        int32x4_t deltas_lo = vdupq_n_s32(0);
        int32x4_t deltas_hi = vdupq_n_s32(0);

        int16x8_t counts = vdupq_n_s16(0);

        int16x8_t centers = vreinterpretq_s16_u16(vld1q_u16(row + x)); //loads 8 values starting at x, so we do 8 times the work at oonce

        for (int offset = -8; offset <= 8; offset++) {
            int16x8_t neighboors   = vreinterpretq_s16_u16(vld1q_u16(row + x + offset)); //neighboor = row[x+offset] (loads 8 values starting at x+offset, since we do 8 columns at the same time)
            int16x8_t deltas_local   = vsubq_s16(neighboors, centers); // row[x+offset] - center
            int16x8_t deltas_local_abs  = vabsq_s16(deltas_local); //abs(delta_local)
            uint16x8_t masks = vcleq_s16(deltas_local_abs, constrast_limit_neon); //mask = (abs(delta_local) <= DESTRIPE_CONTRAST_LIMIT)
            int16x8_t masked_deltas_local  = vandq_s16(deltas_local, vreinterpretq_s16_u16(masks)); // masked_delta_local = delta_local*mask

            deltas_lo = vaddw_s16(deltas_lo, vget_low_s16(masked_deltas_local));   //delta += masked_delta_local (part 1)
            deltas_hi = vaddw_s16(deltas_hi, vget_high_s16(masked_deltas_local));  //delta += masked_delta_local (part 2)
            counts  = vsubq_s16(counts, vreinterpretq_s16_u16(masks)); // count += mask
        }

        // finish the 8 lanes scalar (votes + divide): runs once per pixel
        int32_t deltas_simple[8]; //non-neon version of deltas_*
        int16_t counts_simple[8]; //non-neon version of counts
        vst1q_s32(deltas_simple,     deltas_lo); //deltas_simple = deltas_* (part 1)
        vst1q_s32(deltas_simple + 4, deltas_hi); //deltas_simple = deltas_* (part 2)
        vst1q_s16(counts_simple,     counts); //counts_simple = counts

        for (int i = 0; i < 8; i++) {
            int count = counts_simple[i];
            int votes = std::min(count, DESTRIPE_VOTE_CAP_plus_1);
            deltas[x + i] += deltas_simple[i] * votes / count;
            counts[x + i] += votes;
        }
    };

    // ===== PASS 1: accumulate per-column deltas/counts =====
    for (int y = 0; y < vision_height; y++) {
        const uint16_t* row = frame + (size_t)y * width;

        int x = 0;
        // left edge, we need mirroring so scalar version
        for (; x < 8 && x < width; x++) scalar_col(row, x);

        // interior, we compute 8 columns at the same time AND can't do mirroring, so we must have 8+8 columns before the edge
        for (; x + 16 <= width; x += 8) neon_col(row, x);

        // right edge, we do the remainder with the scalar version
        for (; x < width; x++) scalar_col(row, x);
    }

    // ===== computing the per-column correction =====
    int32_t corr[width];
    for (int x = 0; x < width; x++)
        corr[x] = deltas[x] / counts[x];

    // ===== PASS 2: apply correction (vectorized add + clamp) =====
    const int32x4_t clamp_min  = vdupq_n_s32(0); //clamp() parameters
    const int32x4_t clamp_max  = vdupq_n_s32(0x3FFF); //clamp() parameters
    for (int y = 0; y < vision_height; y++) {
        uint16_t* row = frame + (size_t)y * width;

        int x = 0;
        for (; x + 8 <= width; x += 8) { //we do 8 rows at once, so we must have 8 before the end
            int16x8_t pixels  = vreinterpretq_s16_u16(vld1q_u16(row + x)); //load 8 rows at once
            int32x4_t pixels_corr_low  = vaddq_s32(vmovl_s16(vget_low_s16(pixels)),  vld1q_s32(&corr[x])); //pixel_corr = pixel[x] + corr[x]
            int32x4_t pixels_corr_high  = vaddq_s32(vmovl_s16(vget_high_s16(pixels)), vld1q_s32(&corr[x + 4]));
            pixels_corr_low = vminq_s32(vmaxq_s32(pixels_corr_low, clamp_min), clamp_max); //result = clamp(pixel_corr ,0,0x3FFF) (part 1)
            pixels_corr_high = vminq_s32(vmaxq_s32(pixels_corr_high, clamp_min), clamp_max); //result = clamp(pixel_corr ,0,0x3FFF) (part 2)
            vst1q_u16(row + x, vcombine_u16(vqmovun_s32(pixels_corr_low), vqmovun_s32(pixels_corr_high))); //row[x] = result
        }
        for (; x < width; x++){ //scalar version of the above
            row[x] = (uint16_t)std::clamp((int)row[x] + corr[x], 0, 0x3FFF);
        }
    }
}
#endif


void InfiFrame::fix_dead_pixels(uint16_t * frame) const{
    for(int y = 0 ; y < vision_height ; y++){
        for(int x = 0 ; x < width ; x++){
            if(gain_k_buffer[x+y*width] != 0){ //not bad pixel
                continue;
            }

            int total = 0;
            int count = 0;
            int distance = 1;
            do{
                if(distance > width) { __android_log_assert(nullptr,"libinficam","Can't find a single pixel that is not dead ?!"); }

                for(int dx = -distance ; dx <= distance ; dx++){
                    for(int dy = -distance ; dy <= distance ; dy++){
                        if(dx == 0 && dy==0){
                            continue;
                        }
                        int corr_x = (width-1) - abs((width-1) - abs(dx+x)); //mirror at the edge of the frame
                        int corr_y = (vision_height-1) - abs((vision_height-1) - abs(dy+y));  //mirror at the edge of the frame
                        if(gain_k_buffer[corr_x+corr_y*width] == 0){ //bad pixel
                            continue;
                        }
                        total += frame[corr_x+corr_y*width];
                        count += 1;
                    }
                }
                distance++;
            } while(count == 0);
            frame[x+y*width] = (uint16_t)round((float)total/(float)count);
        }
    }
}

