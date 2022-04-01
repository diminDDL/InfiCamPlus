#extension GL_OES_EGL_image_external : require

precision highp float; // TODO mediump
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

#define FXAA_SUBPIX_DISTANCE (0.5) // TODO 0.5
#define FXAA_SPAN_MAX (0.5) // (8.0) originally TODO
#define FXAA_REDUCE_MIN (0.0) // (1.0 / 128.0) originally
#define FXAA_REDUCE_MUL (0.0) // (1.0 / 8.0) originally

/* Adapted from: https://www.geeks3d.com/20110405/ */
void main(void) {
	vec2 rcpFrame = 1.0 / texSize;
	vec2 pOffset = rcpFrame * FXAA_SUBPIX_DISTANCE;
	vec3 rgbNW = texture2D(sTexture, texCoord + vec2(-pOffset.x, -pOffset.y)).xyz;
	vec3 rgbNE = texture2D(sTexture, texCoord + vec2(pOffset.x, -pOffset.y)).xyz;
	vec3 rgbSW = texture2D(sTexture, texCoord + vec2(-pOffset.x, pOffset.y)).xyz;
	vec3 rgbSE = texture2D(sTexture, texCoord + vec2(pOffset.x, pOffset.y)).xyz;
	vec3 rgbM  = texture2D(sTexture, texCoord).xyz;
	/*---------------------------------------------------------*/
	vec3 luma = vec3(0.299, 0.587, 0.114);
	float lumaNW = dot(rgbNW, luma);
	float lumaNE = dot(rgbNE, luma);
	float lumaSW = dot(rgbSW, luma);
	float lumaSE = dot(rgbSE, luma);
	float lumaM  = dot(rgbM, luma);
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
	//dir = clamp(dir * rcpDirMin, -FXAA_SPAN_MAX, FXAA_SPAN_MAX) * 0.5;
	/*--------------------------------------------------------*/
	//dir = abs(dir);
	/*vec3 rgbA = (1.0 / 2.0) * (
			texture2D(sTexture, texCoord + dir * (1.0 / 3.0 - 0.5)).xyz +
			texture2D(sTexture, texCoord + dir * (2.0 / 3.0 - 0.5)).xyz);
	vec3 rgbB = rgbA * (1.0 / 2.0) + (1.0 / 4.0) * (
			texture2D(sTexture, texCoord + dir * (0.0 / 3.0 - 0.5)).xyz +
			texture2D(sTexture, texCoord + dir * (3.0 / 3.0 - 0.5)).xyz);*/
	dir *= 1.0;
	dir = clamp(dir, -1.0, 1.0);
	vec3 rgbNX = mix(rgbNW, rgbNE, 0.5 + dir.x);
	vec3 rgbSX = mix(rgbSW, rgbSE, 0.5 + dir.x);
	vec3 rgbY = mix(rgbNX, rgbSX, 0.5 + dir.y);
	//gl_FragColor = vec4(rgbY, 1.0);
	//rgbY = vec3(0.0);
	//gl_FragColor = vec4(mix(rgbM, rgbY, sqrt(dir.x * dir.x + dir.y * dir.y)/* / sqrt(2.0) */), 1.0);
	gl_FragColor = vec4(mix(rgbM, rgbY, length(fract(texCoord * texSize + 0.5) - 0.5)/* / sqrt(2.0) */), 1.0);
	/*if (dir.x > 0.0) {
		if (dir.y > 0.0) {
			vec3 rgbNX = mix(rgbNW, rgbNE, dir.x);
			vec3 rgbSX = mix(rgbSW, rgbSE, dir.x);
			vec3 rgbY = mix(rgbSX, rgbNX, dir.y);
			gl_FragColor = vec4(rgbY, 1.0);
		} else {
			vec3 rgbX = mix(rgbM, rgbSE, dir.x);
			vec3 rgbY = mix(rgbM, rgbX, -dir.y);
			gl_FragColor = vec4(rgbY, 1.0);
		}
	} else {
		if (dir.y > 0.0) {
			vec3 rgbX = mix(rgbNE, rgbNW, -dir.x);
			vec3 rgbY = mix(rgbM, rgbX, dir.y);
			gl_FragColor = vec4(rgbY, 1.0);
		} else {
			vec3 rgbX = mix(rgbM, rgbSW, -dir.x);
			vec3 rgbY = mix(rgbM, rgbX, -dir.y);
			gl_FragColor = vec4(rgbY, 1.0);
		}
	}*/
	//gl_FragColor = vec4(rgbY, 1.0);

	/*float lumaB = dot(rgbB, luma);
	if ((lumaB < lumaMin) || (lumaB > lumaMax))
		gl_FragColor = vec4(rgbA, 1.0);
	else gl_FragColor = vec4(rgbB, 1.0);*/
	//gl_FragColor = mix(gl_FragColor, vec4(0.5 + dir.x * 80.0, 0.5 + dir.y * 60.0, rcpDirMin * 0.001, 1.0), 1.0);
	//gl_FragColor = vec4(clamp(dir.x * 100.0, 0.0, 1.0), 0.0, 0.0, 1.0);
}
