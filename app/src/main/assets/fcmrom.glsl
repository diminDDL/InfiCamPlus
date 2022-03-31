#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/*
 * Four tap Catmull-Rom upsampling ( from https://www.shadertoy.com/view/NlBXWR )
 *
 * This takes the ideas from:
 *   https://vec3.ca/bicubic-filtering-in-fewer-taps/
 *     9 taps
 *   https://research.activision.com/publications/archives/filmic-smaasharp-morphological-and-temporal-antialiasing
 *     5 taps, with 4 removed sidelobes
 *
 * This example:
 *   4 taps, with 0 removed sidelobes (but checkerboard preprocessing)
 *
 * By inverting the source texture in a checkerboard style (which can be baked into
 *   the texture or previous render pass) and utilising existing bilinear filtering,
 *   Catmull-Rom reduces from 16 to 4 texture reads.
 *
 * Every other pixel of the source texture is inverted in a checkerboard pattern.
 * This matches the positive/negative flips of the Catmull-Rom lobes.
 *
 * offset works out the position where bilinear filtering will get the correct ratio of
 *   sidelobes. This allows 1 texture fetch to read 4 taps. This needs to be a 4th order
 *   polynomial to preserve partition of unity, but only 2dp of precision is needed.
 * The polynomial coefficients were derived using Excel's trendline function. X and Y
 *   negative and positive sides are evaluated simultaneously in the vec4.
 *
 * w is the appropriate final weighting of the reads, assuming each read is 2D lerp of 4
 *   taps. The some weights are inverted to compensate for the source texture checkerboard.
 *
 * This checkerboard strategy only works where each sidelobe is of opposing sign.
 *   So it works for Catmull-Rom and Lanczos-2, but not Mitchell–Netravali.
 *
 * I have chosen Catmull-Rom over Lanczos-2 as it has a partition of unity, which induces
 *   less ripple in solid colours where local clamping can't be easily done.
 *
 */
void main(void) {
	// Split into whole pixel and subpixel position
	vec2 src_subpixel = texCoord * texSize + 0.5;
	vec2 src_pixel = floor(src_subpixel);
	src_subpixel = fract(src_subpixel);

	// Map texel offsets and weights
	vec4 f = vec4(src_subpixel, 1.0 - src_subpixel);
	// Offset adds the correct ratio of each lobe using texture bilinear interpolation
	//vec4 offset = (((-.94117*f+1.67489)*f-1.2601)*f+.52875)*f+.49921; // Catmull Rom
	vec4 offset = (((-0.94 * f + 1.68) * f - 1.26) * f + 0.53) * f + 0.5; // Catmull Rom
	vec4 texpos = (src_pixel.xyxy + vec4(-offset.xy, offset.zw)) / texSize.xyxy;
	// Weight adds textures in correct ratio, and corrects for checkerboard across kernel.
	vec4 w = ((2.0 * f - 3.5) * f + 0.5) * f + 1.0; // Catmull Rom

	// Texture lookup
	vec3 col = w.x * w.y * (texture2D(sTexture, texpos.xy).xyz * 2.0 - 1.0);
	col -= w.z * w.y * (texture2D(sTexture, texpos.zy).xyz * 2.0 - 1.0);
	col += w.z * w.w * (texture2D(sTexture, texpos.zw).xyz * 2.0 - 1.0);
	col -= w.x * w.w * (texture2D(sTexture, texpos.xw).xyz * 2.0 - 1.0);

	// De-checkerboard
	float z = mod(src_pixel.x + src_pixel.y, 2.0);
	if (z > 0.5) col = -col;

	// Catmull-Rom can ring, so clamp.
	// It would be nice to clamp to local min/max, but that would require additional
	// texture reads. If texture reads are done as a textureGather, this would be
	// possible.
	gl_FragColor.xyz = clamp(col, 0.0, 1.0);

	gl_FragColor.w = 1.;
}
