#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening, adaptiveness;

float luma(vec4 c) {
	return dot(c.xyz, vec3(0.299, 0.587, 0.114));
}

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

		float max = luma(px);
		float min = max;
		max = max(luma(a), max);
		max = max(luma(b), max);
		max = max(luma(c), max);
		max = max(luma(d), max);
		min = min(luma(a), min);
		min = min(luma(b), min);
		min = min(luma(c), min);
		min = min(luma(d), min);

		float diff = min((max - min) * 8.0, 1.0);
		px = mix(px, spx, sharpening * mix(1.0, diff, adaptiveness));
		//px = mix(px, vec4(1.0, 0.0, 0.0, 1.0), sharpening * mix(1.0, diff, adaptiveness) * 0.5);
	}
	gl_FragColor = px;
}
