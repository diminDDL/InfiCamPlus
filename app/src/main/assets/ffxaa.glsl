#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

#define FXAA_SUBPIX_SHIFT (0.0) // (1.0 / 4.0) originally
#define FXAA_SPAN_MAX (1.0) // (8.0) originally
#define FXAA_REDUCE_MIN (0.0) // (1.0 / 128.0) originally
#define FXAA_REDUCE_MUL (0.0) // (1.0 / 8.0) originally

/* Adapted from: https://www.geeks3d.com/20110405/ */
vec3 FxaaPixelShader(vec4 posPos, vec2 rcpFrame) {
	vec3 rgbNW = texture2D(sTexture, posPos.zw).xyz;
	vec3 rgbNE = texture2D(sTexture, posPos.zw + vec2(1.0, 0.0) * rcpFrame).xyz;
	vec3 rgbSW = texture2D(sTexture, posPos.zw + vec2(0.0, 1.0) * rcpFrame).xyz;
	vec3 rgbSE = texture2D(sTexture, posPos.zw + vec2(1.0, 1.0) * rcpFrame).xyz;
	vec3 rgbM  = texture2D(sTexture, posPos.xy).xyz;
	/*---------------------------------------------------------*/
	vec3 luma = vec3(0.299, 0.587, 0.114);
	float lumaNW = dot(rgbNW, luma);
	float lumaNE = dot(rgbNE, luma);
	float lumaSW = dot(rgbSW, luma);
	float lumaSE = dot(rgbSE, luma);
	float lumaM  = dot(rgbM,  luma);
	/*---------------------------------------------------------*/
	float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
	float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
	/*---------------------------------------------------------*/
	vec2 dir;
	dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
	dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));
	/*---------------------------------------------------------*/
	float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * FXAA_REDUCE_MUL),
			FXAA_REDUCE_MIN);
	float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
	dir = min(vec2(FXAA_SPAN_MAX, FXAA_SPAN_MAX),
			max(vec2(-FXAA_SPAN_MAX, -FXAA_SPAN_MAX), dir * rcpDirMin)) * rcpFrame.xy;
	/*--------------------------------------------------------*/
	vec3 rgbA = (1.0 / 2.0) * (
			texture2D(sTexture, posPos.xy + dir * (1.0 / 3.0 - 0.5)).xyz +
			texture2D(sTexture, posPos.xy + dir * (2.0 / 3.0 - 0.5)).xyz);
	vec3 rgbB = rgbA * (1.0 / 2.0) + (1.0 / 4.0) * (
			texture2D(sTexture, posPos.xy + dir * (0.0 / 3.0 - 0.5)).xyz +
			texture2D(sTexture, posPos.xy + dir * (3.0 / 3.0 - 0.5)).xyz);
	float lumaB = dot(rgbB, luma);
	if ((lumaB < lumaMin) || (lumaB > lumaMax))
		return rgbA;
	return rgbB;
}

void main(void) {
	vec2 rcpFrame = 1.0 / texSize;
	vec4 posPos;
	posPos.xy = texCoord;
	posPos.zw = texCoord - rcpFrame * (0.5 + FXAA_SUBPIX_SHIFT);
	gl_FragColor = vec4(FxaaPixelShader(posPos, rcpFrame), 1.0);
}
