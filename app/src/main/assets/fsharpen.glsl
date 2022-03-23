#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening, adaptiveness;

void main(void) { /* Awesome page: https://setosa.io/ev/image-kernels/ */
	vec4 px = texture2D(sTexture, texCoord);
	if (sharpening > 0.0) {
		float mul = 2.0;
		vec2 ts = 1.0 / texSize;
		vec4 spx = px * (1.0 + 4.0 * mul);
		spx -= texture2D(sTexture, texCoord + vec2(0.0, -ts.y)) * mul;
		spx -= texture2D(sTexture, texCoord + vec2(0.0, ts.y)) * mul;
		spx -= texture2D(sTexture, texCoord + vec2(-ts.x, 0)) * mul;
		spx -= texture2D(sTexture, texCoord + vec2(ts.x, 0)) * mul;
		float diff = dot(px.xyz - spx.xyz, vec3(0.299, 0.587, 0.114));
		px = mix(px, spx, sharpening * mix(1.0, abs(diff), adaptiveness));
	}
	gl_FragColor = px;
}
