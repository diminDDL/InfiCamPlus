#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening;

void main(void) { /* Awesome page: https://setosa.io/ev/image-kernels/ */
	vec4 px = texture2D(sTexture, texCoord);
	if (sharpening > 0.0) {
		float mul = 2.0;
		vec2 ts = 1.0 / texSize;
		vec4 a = texture2D(sTexture, texCoord + vec2(0.0, -ts.y));
		vec4 b = texture2D(sTexture, texCoord + vec2(0.0, ts.y));
		vec4 c = texture2D(sTexture, texCoord + vec2(-ts.x, 0));
		vec4 d = texture2D(sTexture, texCoord + vec2(ts.x, 0));
		vec4 spx = px * (1.0 + 4.0 * mul) - (a + b + c + d) * mul;
		px = mix(px, spx, sharpening);
	}
	gl_FragColor = px;
}
