#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/* Checkerboard invert half the texture pixels
 *
 * This could be baked into the tone mapping of the previous render pass.
 *
 * The checkerboarding allows bilinear filtering to be used to read 4 taps
 * simultaneously at the correct ratios, by strategic selection of the subpixel
 * position. 16 taps are read as 4 texture reads.
 *
 * This buffer should be the same size as the source image.
 */
void main(void) {
	// Pixel number, integer
	vec2 pixel = floor(texCoord * texSize);

	// Get texture, 1:1 pixel ratio, sampling at centre of texel.
	vec3 c = texture2D(sTexture, (pixel + 0.5) / texSize).xyz;

	// Checkerboard flip
	float flip = (pixel.x + pixel.y) * 0.5;
	flip = flip - floor(flip);
	if (flip > 0.25) c = -c; // TODO verify this is different than 0 and why

	gl_FragColor = vec4(c * 0.5 + 0.5, 1.0);
}
