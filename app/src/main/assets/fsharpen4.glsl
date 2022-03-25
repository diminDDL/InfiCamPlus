#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening;

/* Gaussian that i determined in Gimp to work well (radius 1.5):
 *    0.1 0.15 0.18 0.15  0.1
 *   0.15 0.22 0.25 0.22 0.15
 *   0.18 0.25  4.2 0.25 0.18
 *   0.15 0.22 0.25 0.22 0.15
 *    0.1 0.15 0.18 0.15  0.1
 *
 * TODO remember that applying vertical and horizontal sequentially could reduce fetches massively
 * TODO   also remember the matrix is different for that, sorta like the diagonal of the 2D one
 * TODO   could we apply this to the cubic interpolation and the likes?
 */
void main(void) {
	vec4 px = texture2D(sTexture, texCoord);
	if (sharpening > 0.0) {
		vec2 ts = 1.0 / texSize;
		vec4 spx = px * 5.2;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y)) * 0.22;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, -ts.y)) * 0.25;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, -ts.y)) * 0.22;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, 0.0)) * 0.25;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, 0.0)) * 0.25;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, ts.y)) * 0.22;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, ts.y)) * 0.25;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, ts.y)) * 0.22;

		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y * 2.0)) * 0.15;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, -ts.y * 2.0)) * 0.18;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, -ts.y * 2.0)) * 0.15;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, ts.y * 2.0)) * 0.15;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, ts.y * 2.0)) * 0.18;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, ts.y * 2.0)) * 0.15;

		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, -ts.y)) * 0.15;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, -ts.y)) * 0.15;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, 0.0)) * 0.18;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, 0.0)) * 0.18;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, ts.y)) * 0.15;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, ts.y)) * 0.15;

		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, -ts.y * 2.0)) * 0.1;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, ts.y * 2.0)) * 0.1;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, -ts.y * 2.0)) * 0.1;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, ts.y * 2.0)) * 0.1;

		px = mix(px, spx, sharpening /* * abs(px - spx) */);
	}
	gl_FragColor = px;
}
