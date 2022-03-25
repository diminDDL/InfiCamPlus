#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening;

void main(void) {
	vec4 px = texture2D(sTexture, texCoord);
	if (sharpening > 0.0) {
		vec2 ts = 1.0 / texSize;
		vec4 spx = px * 233.0;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y)) * 16.0;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, -ts.y)) * 26.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, -ts.y)) * 16.0;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, 0.0)) * 26.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, 0.0)) * 26.0;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, ts.y)) * 16.0;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, ts.y)) * 26.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, ts.y)) * 16.0;

		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y * 2.0)) * 4.0;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, -ts.y * 2.0)) * 7.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, -ts.y * 2.0)) * 4.0;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, ts.y * 2.0)) * 4.0;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, ts.y * 2.0)) * 7.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, ts.y * 2.0)) * 4.0;

		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, -ts.y)) * 4.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, -ts.y)) * 4.0;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, 0.0)) * 7.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, 0.0)) * 7.0;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, ts.y)) * 4.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, ts.y)) * 4.0;

		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, -ts.y * 2.0)) * 1.0;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x * 2.0, ts.y * 2.0)) * 1.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, -ts.y * 2.0)) * 1.0;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x * 2.0, ts.y * 2.0)) * 1.0;

		px = mix(px, spx, sharpening * 0.015 /* * abs(px - spx) */);
	}
	gl_FragColor = px;
}
