#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

void main(void) {
	vec2 ts = 1.0 / texSize;

	vec4 n[9];
	n[0] = texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y));
	n[1] = texture2D(sTexture, texCoord + vec2(0.0, -ts.y));
	n[2] = texture2D(sTexture, texCoord + vec2(ts.x, -ts.y));
	n[3] = texture2D(sTexture, texCoord + vec2(-ts.x, 0.0));
	n[4] = texture2D(sTexture, texCoord);
	n[5] = texture2D(sTexture, texCoord + vec2(ts.x, 0.0));
	n[6] = texture2D(sTexture, texCoord + vec2(-ts.x, ts.y));
	n[7] = texture2D(sTexture, texCoord + vec2(0.0, ts.y));
	n[8] = texture2D(sTexture, texCoord + vec2(ts.x, ts.y));

	vec4 edgex = n[2] + 2.0 * n[5] + n[8] - (n[0] + 2.0 * n[3] + n[6]);
	vec4 edgey = n[0] + 2.0 * n[1] + n[2] - (n[6] + 2.0 * n[7] + n[8]);
	vec4 sobel = sqrt((edgex * edgex) + (edgey * edgey));

	gl_FragColor = vec4(1.0, 1.0, 1.0, dot(sobel.rgb, vec3(0.299, 0.587, 0.114)));
}
