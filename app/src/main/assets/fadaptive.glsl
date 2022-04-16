/* See: https://ieeexplore.ieee.org/document/924383 */
#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/* ITU BT.601, but normalized. */
float luma(vec3 px) {
	return (px.r * 0.299 + px.g * 0.587 + px.b * 0.114) / (0.299 + 0.587 + 0.114);
}

void cmrom(float x, out float w[4]) {
	w[0] = x * (-0.5 + x * (1.0 - 0.5 * x));
	w[1] = 1.0 + x * x * (-2.5 + 1.5 * x);
	w[2] = x * (0.5 + x * (2.0 - 1.5 * x));
	w[3] = x * x * (-0.5 + 0.5 * x);
}

vec3 cmrom2d(vec3 c[16], vec2 d) {
	float wx[4], wy[4];
	cmrom(d.x, wx);
	cmrom(d.y, wy);
	vec3 ret = vec3(0.0);
	ret += c[0] * wx[0] * wy[0];
	ret += c[1] * wx[1] * wy[0];
	ret += c[2] * wx[2] * wy[0];
	ret += c[3] * wx[3] * wy[0];
	ret += c[4] * wx[0] * wy[1];
	ret += c[5] * wx[1] * wy[1];
	ret += c[6] * wx[2] * wy[1];
	ret += c[7] * wx[3] * wy[1];
	ret += c[8] * wx[0] * wy[2];
	ret += c[9] * wx[1] * wy[2];
	ret += c[10] * wx[2] * wy[2];
	ret += c[11] * wx[3] * wy[2];
	ret += c[12] * wx[0] * wy[3];
	ret += c[13] * wx[1] * wy[3];
	ret += c[14] * wx[2] * wy[3];
	ret += c[15] * wx[3] * wy[3];
	return ret;
}

float G(vec3 c[16], int o) {
	float gx = 0.0, gy = 0.0;
	float n = 2.0; /* The paper specifies sobel, prewitt (n=1) also seems to work well. */
	gx += (-luma(c[o - 1]) + luma(c[o + 1])) * n;
	gx += -luma(c[o - 4 - 1]) + luma(c[o - 4 + 1]);
	gx += -luma(c[o + 4 - 1]) + luma(c[o + 4 + 1]);
	gy += (luma(c[o - 4]) + -luma(c[o + 4])) * n;
	gy += luma(c[o - 4 - 1]) + -luma(c[o + 4 - 1]);
	gy += luma(c[o - 4 + 1]) + -luma(c[o + 4 + 1]);
	return (abs(gx / 3.0) + abs(gy / 3.0)) / 2.0;
}

float W(vec3 c[16], int o) {
	float mu = 0.5; /* Should be a positive value close to and lower than one. */
	float n = 2.0; /* Should be positive value. */
	return pow(1.0 - mu * G(c, o), n);
}

float D(vec2 d) {
	return (1.0 - d.x) * (1.0 - d.y);
	//return 1.0 - clamp(length(d), 0.0, 1.0);
}

void main() {
	vec2 ts = 1.0 / texSize;
	vec2 p = texCoord * texSize;
	vec2 lp = fract(p);
	vec2 tl = (floor(p) - 1.0);

	vec3 c[16];
	c[0] = texture2D(sTexture, (tl + vec2(0, 0)) * ts).rgb;
	c[1] = texture2D(sTexture, (tl + vec2(1, 0)) * ts).rgb;
	c[2] = texture2D(sTexture, (tl + vec2(2, 0)) * ts).rgb;
	c[3] = texture2D(sTexture, (tl + vec2(3, 0)) * ts).rgb;
	c[4] = texture2D(sTexture, (tl + vec2(0, 1)) * ts).rgb;
	c[5] = texture2D(sTexture, (tl + vec2(1, 1)) * ts).rgb;
	c[6] = texture2D(sTexture, (tl + vec2(2, 1)) * ts).rgb;
	c[7] = texture2D(sTexture, (tl + vec2(3, 1)) * ts).rgb;
	c[8] = texture2D(sTexture, (tl + vec2(0, 2)) * ts).rgb;
	c[9] = texture2D(sTexture, (tl + vec2(1, 2)) * ts).rgb;
	c[10] = texture2D(sTexture, (tl + vec2(2, 2)) * ts).rgb;
	c[11] = texture2D(sTexture, (tl + vec2(3, 2)) * ts).rgb;
	c[12] = texture2D(sTexture, (tl + vec2(0, 3)) * ts).rgb;
	c[13] = texture2D(sTexture, (tl + vec2(1, 3)) * ts).rgb;
	c[14] = texture2D(sTexture, (tl + vec2(2, 3)) * ts).rgb;
	c[15] = texture2D(sTexture, (tl + vec2(3, 3)) * ts).rgb;

	float wts[4];
	wts[0] = D(lp) * W(c, 5);
	wts[1] = D(vec2(1.0 - lp.x, lp.y)) * W(c, 6);
	wts[2] = D(vec2(lp.x, 1.0 - lp.y)) * W(c, 9);
	wts[3] = D(vec2(1.0 - lp.x, 1.0 - lp.y)) * W(c, 10);
	float wtot = wts[0] + wts[1] + wts[2] + wts[3];
	for (int i = 0; i < 4; ++i)
		wts[i] /= wtot;

	//gl_FragColor = vec4(c[5] * wts[0] + c[6] * wts[1] + c[9] * wts[2] + c[10] * wts[3], 1.0);
	//gl_FragColor = texture2D(sTexture, (floor(texCoord * texSize) + vec2(wts[1] + wts[3], wts[2] + wts[3])) * ts);
	gl_FragColor = vec4(cmrom2d(c, vec2(wts[1] + wts[3], wts[2] + wts[3])), 1.0);
}
