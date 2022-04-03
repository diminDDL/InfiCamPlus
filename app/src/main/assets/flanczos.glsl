#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

#define M_PI (radians(180.0))

float lanc2(float x) {
	float a = 2.0;
	if (x == 0.0)
		return 1.0;
	if (x <= -a || x >= a)
		return 0.0;
	return (a * sin(M_PI * x) * sin(M_PI * x / a)) / (M_PI * M_PI * x * x);
}

void main(void) {
	vec2 ts = 1.0 / texSize;
	vec2 iuv = floor(texCoord * texSize) + 0.5;
	vec2 fuv = fract(texCoord * texSize) - 0.5;
	vec3 c0 = texture2D(sTexture, iuv * ts).rgb;
	vec3 cN = texture2D(sTexture, (iuv + vec2(0.0, -1.0)) * ts).rgb;
	vec3 cS = texture2D(sTexture, (iuv + vec2(0.0, 1.0)) * ts).rgb;
	vec3 cW = texture2D(sTexture, (iuv + vec2(-1.0, 0.0)) * ts).rgb;
	vec3 cE = texture2D(sTexture, (iuv + vec2(1.0, 0.0)) * ts).rgb;
	vec3 cNW = texture2D(sTexture, (iuv + vec2(-1.0, -1.0)) * ts).rgb;
	vec3 cNE = texture2D(sTexture, (iuv + vec2(1.0, -1.0)) * ts).rgb;
	vec3 cSW = texture2D(sTexture, (iuv + vec2(-1.0, 1.0)) * ts).rgb;
	vec3 cSE = texture2D(sTexture, (iuv + vec2(1.0, 1.0)) * ts).rgb;

	float w0 = lanc2(length(fuv));
	float wN = lanc2(length(fuv - vec2(0.0, -1.0)));
	float wS = lanc2(length(fuv - vec2(0.0, 1.0)));
	float wW = lanc2(length(fuv - vec2(-1.0, 0.0)));
	float wE = lanc2(length(fuv - vec2(1.0, 0.0)));
	float wNW = lanc2(length(fuv - vec2(-1.0, -1.0)));
	float wNE = lanc2(length(fuv - vec2(1.0, -1.0)));
	float wSW = lanc2(length(fuv - vec2(-1.0, 1.0)));
	float wSE = lanc2(length(fuv - vec2(1.0, 1.0)));

	vec3 col = c0 * w0 + cN * wN + cS * wS + cW * wW + cE * wE;
	col += cNW * wNW + cNE * wNE + cSW * wSW + cSE * wSE;
	col /= w0 + wN + wS + wW + wE + wNW + wNE + wSW + wSE;
	gl_FragColor = vec4(col, 1.0);
}
