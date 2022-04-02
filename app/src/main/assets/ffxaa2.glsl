#extension GL_OES_EGL_image_external : require

precision highp float; // TODO mediump
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

#define M_PI (3.1415926535897932384626433832795)

float luma(vec3 col) {
	return dot(col, vec3(0.299, 0.587, 0.114));
}

vec2 rotate(vec2 v, float a) {
	float s = sin(a);
	float c = cos(a);
	mat2 m = mat2(c, -s, s, c);
	return m * v;
}

void main(void) {
	vec2 span = 1.0 / texSize * 0.5;
	vec3 cOrg = texture2D(sTexture, texCoord).rgb;
	vec3 cW = texture2D(sTexture, texCoord + vec2(-1.0, 0.0) * span).rgb;
	vec3 cE = texture2D(sTexture, texCoord + vec2(1.0, 0.0) * span).rgb;
	vec3 cN = texture2D(sTexture, texCoord + vec2(0.0, -1.0) * span).rgb;
	vec3 cS = texture2D(sTexture, texCoord + vec2(0.0, 1.0) * span).rgb;
	vec3 cNW = texture2D(sTexture, texCoord + vec2(-1.0, -1.0) * span).rgb;
	vec3 cNE = texture2D(sTexture, texCoord + vec2(1.0, -1.0) * span).rgb;
	vec3 cSW = texture2D(sTexture, texCoord + vec2(-1.0, 1.0) * span).rgb;
	vec3 cSE = texture2D(sTexture, texCoord + vec2(1.0, 1.0) * span).rgb;

	vec3 edge = cOrg * 12.0 - ((cW + cE + cN + cS) * 2.0 + cNW + cNE + cSW + cSE);
	vec3 edgex = cNW + cN * 2.0 + cNE + -cSW + -cS * 2.0 + -cSE;
	vec3 edgey = -cNW + -cW * 2.0 + -cSW + cNE + cE * 2.0 + cSE;
	vec3 sobel = sqrt((edgex * edgex) + (edgey * edgey));
	vec3 e = (cNW + cSE) - (cNE + cSW);

	vec2 iuv = floor(texCoord * texSize) + 0.5;
	vec2 fuv = fract(texCoord * texSize) - 0.5;
	//fuv = rotate(fuv, M_PI * 0.25);
	vec3 col = (cOrg * 4.0 + /*(cW + cE + cN + cS) * 2.0 + */(cNW + cNE + cSW + cSE)) / 8.0;
	col = mix(cOrg, col, clamp(abs(luma(sobel)), 0.0, 1.0));
	//gl_FragColor = vec4(vec3(abs(luma(sobel))), 1.0);
	gl_FragColor = vec4(col, 1.0);
}
