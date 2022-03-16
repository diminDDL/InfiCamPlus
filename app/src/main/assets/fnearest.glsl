#extension GL_OES_EGL_image_external : require

precision highp float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening;

/* We do nearest-neighbor interpolation by floor() manually because otherwise our sharpening filter
 *   won't pick up the exact right pixel value (if exactly on the edge of a pixel it could get one
 *   pixel over rather than the adjacent one).
 */
void main(void) {
	vec2 uv = (floor(texCoord * texSize) + 0.5) / texSize;
	vec4 px = texture2D(sTexture, uv);
	if (sharpening > 0.0) { /* Awesome page: https://setosa.io/ev/image-kernels/ */
		vec2 ts = 1.0 / texSize;
		vec4 spx = px * 5.0;
		spx -= texture2D(sTexture, uv + vec2(0.0, -ts.y));
		spx -= texture2D(sTexture, uv + vec2(0.0, ts.y));
		spx -= texture2D(sTexture, uv + vec2(-ts.x, 0));
		spx -= texture2D(sTexture, uv + vec2(ts.x, 0));
		px = mix(px, spx, sharpening);
	}
	gl_FragColor = px;
}
