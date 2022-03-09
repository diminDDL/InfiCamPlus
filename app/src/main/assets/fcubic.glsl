#extension GL_OES_EGL_image_external : require

precision mediump float;
varying vec2 texCoord;
uniform vec2 texSize;
uniform samplerExternalOES sTexture;

/* Uniform cubic B-spline basis functions. */
vec4 cubic(float x) {
	float x2 = x * x;
	float x3 = x2 * x;
	return vec4(
		-x3 + 3.0 * x2 - 3.0 * x + 1.0,
		3.0 * x3 - 6.0 * x2 + 4.0,
		-3.0 * x3 + 3.0 * x2 + 3.0 * x + 1.0,
		x3
	) / 6.0;
}

/* Do 4x4 cubic interpolation with remarkably few lookups by abusing the linear interpolation done
 *   by EGL. This is not my idea, I pieced it together from things on StackOverflow and various
 *   other sources, most notably:
 *     https://stackoverflow.com/questions/13501081/efficient-bicubic-filtering-code-in-glsl
 */
vec4 texcubic(sampler2D tex, vec2 uv, vec2 res) {
	uv = uv * res + 0.5; /* Center of the closest texel multiplied in bitmap coords. */
	vec2 iuv = floor(uv); /* Top left of that pixel. */
	vec2 fuv = fract(uv); /* How far off the top left of that we are. */
	vec4 xc = cubic(fuv.x); /* Spline basis weights for X offsets. */
	vec4 yc = cubic(fuv.y); /* Spline basis weights for Y offsets. */

	/* Combine the X and Y weights so we have a weight for each pixel. */
	vec4 s = vec4(xc.x + xc.y, xc.z + xc.w, yc.x + yc.y, yc.z + yc.w);

	/* We put the nearest 4 pixels in c. */
	vec4 c = vec4(iuv.x - 1.0, iuv.x + 1.0, iuv.y - 1.0, iuv.y + 1.0);

	/* Figure out which positions to sample. */
	vec4 pos = c + vec4(xc.y, xc.w, yc.y, yc.w) / s - 0.5;

	/* Sample the 4 already linearly interpolated points. */
	vec4 s0 = texture2D(tex, vec2(pos.x, pos.z) / res);
	vec4 s1 = texture2D(tex, vec2(pos.y, pos.z) / res);
	vec4 s2 = texture2D(tex, vec2(pos.x, pos.w) / res);
	vec4 s3 = texture2D(tex, vec2(pos.y, pos.w) / res);

	/* Get weights per row/column. */
	float sx = s.x / (s.x + s.y);
	float sy = s.z / (s.z + s.w);

	/* Mix the rows and columns. */
	return mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
}

void main(void) {
	gl_FragColor = texcubic(sTexture, texCoord, texSize);
}
