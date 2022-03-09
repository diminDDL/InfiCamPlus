#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 texCoord;
uniform samplerExternalOES sTexture;
uniform vec2 invScreenSize;

/* Uniform cubic B-spline basis functions. */
/*vec4 cubic(float x) {
	float x2 = x * x;
	float x3 = x2 * x;
	return vec4(
		-x3 + 3.0 * x2 - 3.0 * x + 1.0,
		3.0 * x3 - 6.0 * x2 + 4.0,
		-3.0 * x3 + 3.0 * x2 + 3.0 * x + 1.0,
		x3
	) / 6.0;
}*/

/* See: DOI:10.3390/mca21030033 */
vec4 cubic(float x) {
	float x2 = x * x;
	float x3 = x2 * x;
	float a = 1.0;
	return vec4(
		(-x + 2.0 * x2 - x3) * a,
		2.0 + (a - 6.0) * x2 + (4.0 - a) * x3,
		x * a + (6.0 - 2.0 * a) * x2 - (4.0 - a) * x3,
		(-x2 + x3) * a
	) * 0.5;
}

vec4 texcubic44(sampler2D tex, vec2 uv, vec2 res) {
	uv = uv * res + 0.5; /* Pixel being drawn mapped to input texture offset by half a pixel. */
	vec2 iuv = floor(uv); /* Top left of that pixel. */
	vec2 fuv = fract(uv); /* How far off the center of it we are. */
	vec4 xc = cubic(fuv.x); /* Spline basis weights for X offset. */
	vec4 yc = cubic(fuv.y); /* Spline basis weights for Y offset. */

	/* Combine the X and Y weights. */
	vec4 s = vec4(xc.x + xc.y, xc.z + xc.w, yc.x + yc.y, yc.z + yc.w);

	/* We put the nearest 4 pixels in c. */
	vec4 c = vec4(iuv.x - 1.0, iuv.x + 1.0, iuv.y - 1.0, iuv.y + 1.0);

	vec4 pos = c + vec4(xc.y, xc.w, yc.y, yc.w) / s - 0.5;

	vec4 s0 = texture2D(tex, vec2(pos.x, pos.z) / res);
	vec4 s1 = texture2D(tex, vec2(pos.y, pos.z) / res);
	vec4 s2 = texture2D(tex, vec2(pos.x, pos.w) / res);
	vec4 s3 = texture2D(tex, vec2(pos.y, pos.w) / res);

	float sx = s.x / (s.x + s.y);
	float sy = s.z / (s.z + s.w);

	return mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
}

void main() {
	// gl_FragColor = texture2D(sTexture,texCoord);
	// TODO can we use gl_FragCoord.xy instead of vertex shader?
	gl_FragColor = texcubic44(sTexture, texCoord, vec2(8.0, 6.0)); // TODO right size
}
