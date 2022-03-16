#extension GL_OES_EGL_image_external : require

precision highp float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening;

void main(void) {
	vec4 px = texture2D(sTexture, texCoord);
	if (sharpening > 0.0) { /* Awesome page: https://setosa.io/ev/image-kernels/ */
		vec2 ts = 1.0 / texSize;
		vec4 spx = px * 5.0;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, -ts.y));
		spx -= texture2D(sTexture, texCoord + vec2(0.0, ts.y));
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, 0));
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, 0));
		px = mix(px, spx, sharpening);
	}
	gl_FragColor = px;
}
