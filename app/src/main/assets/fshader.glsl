#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening;

void main(void) {
	vec4 px = texture2D(sTexture, texCoord);
	if (sharpening > 0.0) {
		vec4 spx = px * 5.0;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, -1.0) / texSize);
		spx -= texture2D(sTexture, texCoord + vec2(0.0, 1.0) / texSize);
		spx -= texture2D(sTexture, texCoord + vec2(-1.0, 0) / texSize);
		spx -= texture2D(sTexture, texCoord + vec2(1.0, 0) / texSize);
		px = mix(px, spx, sharpening);
	}
	gl_FragColor = px;
}
