#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/* Uniform cubic B-spline basis functions. */
vec4 cubic(const in float x) {
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
 * Apparently on some phones passing the samplerExternalOES as a parameter of sampler2D type fails
 *   because the shader compiler refuses to see the function as a fitting overload (casting didn't
 *   seem to work either) and on other phones having the parameter be typed samplerExternalOES
 *   fails for whatever reason, so i just dumped it all in main().
 */
void main(void) {
	vec2 uv = texCoord * texSize + 0.5; /* Center of the closest texel in bitmap coords. */
	vec2 iuv = floor(uv); /* Top left of that pixel. */
	vec2 fuv = fract(uv); /* How far off the top left of that we are. */
	vec4 xc = cubic(fuv.x); /* Spline basis weights for X offsets. */
	vec4 yc = cubic(fuv.y); /* Spline basis weights for Y offsets. */

	/* Combine the X and Y weights so we have a weight for each pixel. */
	vec4 s = vec4(xc.x + xc.y, xc.z + xc.w, yc.x + yc.y, yc.z + yc.w);

	/* We put the nearest 4 pixels in c. */
	vec4 c = iuv.xxyy + vec4(-1.0, 1.0, -1.0, +1.0);

	/* Figure out which positions to sample. */
	vec4 pos = c + vec4(xc.y, xc.w, yc.y, yc.w) / s - 0.5;

	/* Sample the 4 already linearly interpolated points. */
	vec4 s0, s1, s2, s3;
	s0 = texture2D(sTexture, pos.xz / texSize);
	s1 = texture2D(sTexture, pos.yz / texSize);
	s2 = texture2D(sTexture, pos.xw / texSize);
	s3 = texture2D(sTexture, pos.yw / texSize);

	/* Get weights per row/column. */
	float sx = s.x / (s.x + s.y);
	float sy = s.z / (s.z + s.w);

	/* Mix the rows and columns. */
	gl_FragColor = mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
}
