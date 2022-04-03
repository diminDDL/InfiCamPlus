#extension GL_OES_EGL_image_external : require

precision highp float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

#define M_PI (radians(180.0))

float lanc2(float x) {
	float a = 2.0;
	if (x == 0.0)
		return 1.0;
	if (abs(x) > a)
		return 0.0;
	//return a * sin(M_PI * x) * sin(M_PI * x / a) / (M_PI * M_PI * x * x);
	return sin(M_PI * x) * sin(M_PI * x / a) / (x * x);
}

float lanc2(vec2 x) { return lanc2(x.x) * lanc2(x.y); }

void main(void) {
	vec2 ts = 1.0 / texSize;
	vec2 iuv = floor(texCoord * texSize) + 0.5;
	vec2 fuv = (texCoord * texSize) - iuv;
	vec3 c0 = texture2D(sTexture, iuv * ts).rgb;
	vec3 cN = texture2D(sTexture, (iuv + vec2(0.0, -1.0)) * ts).rgb;
	vec3 cS = texture2D(sTexture, (iuv + vec2(0.0, 1.0)) * ts).rgb;
	vec3 cW = texture2D(sTexture, (iuv + vec2(-1.0, 0.0)) * ts).rgb;
	vec3 cE = texture2D(sTexture, (iuv + vec2(1.0, 0.0)) * ts).rgb;
	vec3 cNW = texture2D(sTexture, (iuv + vec2(-1.0, -1.0)) * ts).rgb;
	vec3 cNE = texture2D(sTexture, (iuv + vec2(1.0, -1.0)) * ts).rgb;
	vec3 cSW = texture2D(sTexture, (iuv + vec2(-1.0, 1.0)) * ts).rgb;
	vec3 cSE = texture2D(sTexture, (iuv + vec2(1.0, 1.0)) * ts).rgb;
	vec3 cNN = texture2D(sTexture, (iuv + vec2(0.0, -2.0)) * ts).rgb;
	vec3 cSS = texture2D(sTexture, (iuv + vec2(0.0, 2.0)) * ts).rgb;
	vec3 cWW = texture2D(sTexture, (iuv + vec2(-2.0, 0.0)) * ts).rgb;
	vec3 cEE = texture2D(sTexture, (iuv + vec2(2.0, 0.0)) * ts).rgb;
	vec3 cNNW = texture2D(sTexture, (iuv + vec2(-1.0, -2.0)) * ts).rgb;
	vec3 cNNE = texture2D(sTexture, (iuv + vec2(1.0, -2.0)) * ts).rgb;
	vec3 cSSW = texture2D(sTexture, (iuv + vec2(-1.0, 2.0)) * ts).rgb;
	vec3 cSSE = texture2D(sTexture, (iuv + vec2(1.0, 2.0)) * ts).rgb;
	vec3 cWWN = texture2D(sTexture, (iuv + vec2(-2.0, -1.0)) * ts).rgb;
	vec3 cWWS = texture2D(sTexture, (iuv + vec2(-2.0, 1.0)) * ts).rgb;
	vec3 cEEN = texture2D(sTexture, (iuv + vec2(2.0, -1.0)) * ts).rgb;
	vec3 cEES = texture2D(sTexture, (iuv + vec2(2.0, 1.0)) * ts).rgb;

	fuv = -fuv;
	float w0 = lanc2(fuv);
	float wN = lanc2(fuv + vec2(0.0, -1.0));
	float wS = lanc2(fuv + vec2(0.0, 1.0));
	float wW = lanc2(fuv + vec2(-1.0, 0.0));
	float wE = lanc2(fuv + vec2(1.0, 0.0));
	float wNW = lanc2(fuv + vec2(-1.0, -1.0));
	float wNE = lanc2(fuv + vec2(1.0, -1.0));
	float wSW = lanc2(fuv + vec2(-1.0, 1.0));
	float wSE = lanc2(fuv + vec2(1.0, 1.0));

	float wNN = lanc2(fuv + vec2(0.0, -2.0));
	float wSS = lanc2(fuv + vec2(0.0, 2.0));
	float wWW = lanc2(fuv + vec2(-2.0, 0.0));
	float wEE = lanc2(fuv + vec2(2.0, 0.0));
	float wNNW = lanc2(fuv + vec2(-1.0, -2.0));
	float wNNE = lanc2(fuv + vec2(1.0, -2.0));
	float wSSW = lanc2(fuv + vec2(-1.0, 2.0));
	float wSSE = lanc2(fuv + vec2(1.0, 2.0));
	float wWWN = lanc2(fuv + vec2(-2.0, -1.0));
	float wWWS = lanc2(fuv + vec2(-2.0, 1.0));
	float wEEN = lanc2(fuv + vec2(2.0, -1.0));
	float wEES = lanc2(fuv + vec2(2.0, 1.0));

	vec3 col = c0 * w0 + cN * wN + cS * wS + cW * wW + cE * wE;
	col += cNW * wNW + cNE * wNE + cSW * wSW + cSE * wSE;
	col += cNN * wNN + cSS * wSS + cWW * wWW + cEE * wEE;
	col += cNNW * wNNW + cNNE * wNNE + cSSW * wSSW + cSSE * wSSE;
	col += cWWN * wWWN + cWWS * wWWS + cEEN * wEEN + cEES * wEES;
	col /= w0 + wN + wS + wW + wE + wNW + wNE + wSW + wSE + wNN + wSS + wWW + wEE +
			wNNW + wNNE + wSSW + wSSE + wWWN + wWWS + wEEN + wEES;
	gl_FragColor = vec4(col, 1.0);
}
