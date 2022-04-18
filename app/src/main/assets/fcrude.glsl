#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/* ITU BT.601, but normalized. */
float luma(vec3 px) {
	return (px.r * 0.299 + px.g * 0.587 + px.b * 0.114) / (0.299 + 0.587 + 0.114);
}

/* Very crude directional interpolation, only intended for testing the idea. */
void main() {
	vec2 ts = 1.0 / texSize;
	vec2 p = texCoord * texSize;

	float mindev = 10.0;
	float phi = 0.0;
	for (float i = 0.0; i < 3.15; i += 0.1) {
		vec3 a = texture2D(sTexture, (p + vec2(sin(i) * -3.5, cos(i) * -3.5)) * ts).rgb;
		vec3 b = texture2D(sTexture, (p + vec2(sin(i) * -1.5, cos(i) * -1.5)) * ts).rgb;
		vec3 c = texture2D(sTexture, (p * ts)).rgb;
		vec3 d = texture2D(sTexture, (p + vec2(sin(i) * +1.5, cos(i) * +1.5)) * ts).rgb;
		vec3 e = texture2D(sTexture, (p + vec2(sin(i) * +3.5, cos(i) * +3.5)) * ts).rgb;
		float dev = abs(luma(a - b)) + abs(luma(b - c)) + abs(luma(c - d)) + abs(luma(d - e));
		if (dev < mindev) {
			phi = i;
			mindev = dev;
		}
	}
	vec3 a = texture2D(sTexture, (p + vec2(sin(phi) * -1.5, cos(phi) * -0.75)) * ts).rgb;
	vec3 b = texture2D(sTexture, (p + vec2(sin(phi) * -0.5, cos(phi) * -0.5)) * ts).rgb;
	vec3 c = texture2D(sTexture, (p + vec2(sin(phi) * +0.5, cos(phi) * +0.5)) * ts).rgb;
	vec3 d = texture2D(sTexture, (p + vec2(sin(phi) * +1.5, cos(phi) * +1.5)) * ts).rgb;
	gl_FragColor = vec4((a + b + c + d) / 4.0, 1.0);
}
