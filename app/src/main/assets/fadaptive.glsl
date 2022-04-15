#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/* ITU BT.601, but normalized. */
float luma(vec3 px) {
	return (px.r * 0.299 + px.g * 0.587 + px.b * 0.114) / (0.299 + 0.587 + 0.114);
}

void cmrom(float x, out double w[]) {
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
	for (int i = 0; i < 16; ++i)
		ret += c[i] * wx[i % 4] * wy[i / 4];
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
	return (abs(gx / 3) + abs(gy / 3)) / 2;
}

float W(vec3 c[16], int o) {
	double mu = 0.95; /* Should be a positive value close to and lower than one. */
	double n = 2.0; /* Should be positive value. */
	return pow(1 - mu * G(c, o), n);
}

float D(vec2 d) {
	return (1 - d.x) * (1 - d.y);
}

void main() {
	vec2 ts = 1.0 / texSize;
	vec2 p = texCoord * texSize;
	vec2 lp = fract(p);
	vec2 tl = (floor(p) - 1) * ts;

	vec3 c[16];
	for (int i = 0; i < 16; ++i)
		c[i] = texture2D(sTexture, tl + vec2(float(i % 4), float(i / 4))).rgb;

/*	float fx = x * in->width / out->width;
	float fy = y * in->height / out->height;
	int ix = floor(fx), iy = floor(fy);
	fx = fx - ix;
	fy = fy - iy;*/

	float wts[] = {
		D(fx, fy) * W(c, 5),
		D(1 - fx, fy) * W(c, 6),
		D(fx, 1 - fy) * W(c, 9),
		D(1 - fx, 1 - fy) * W(c, 10)
	};
	double wtot = wts[0] + wts[1] + wts[2] + wts[3];
	for (int i = 0; i < 4; ++i)
	wts[i] /= wtot;

	//printf("%f %f -- (%f) %f %f %f %f\n", fx, fy, wtot, wts[0], wts[1], wts[2], wts[3]);

	/*int sc[] = { c[5], c[6], c[9], c[10] };
	img_setpx(out, x, y, weightcolors(sc, wts, 4));*/

	// Attempt at extending this to use a cubic spline.
	vec3 cn = cmrom2d(c, vec2(wts[1] + wts[3], wts[2] + wts[3]));
	img_setpx(out, x, y, cn);
}
