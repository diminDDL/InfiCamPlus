#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

// TODO check https://en.wikipedia.org/wiki/Sobel_operator#Alternative_operators
void main(void) {
	vec2 ts = 1.0 / texSize;
	vec4 a = texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y));
	vec4 b = texture2D(sTexture, texCoord + vec2(0.0, -ts.y)) * 2.0;
	vec4 c = texture2D(sTexture, texCoord + vec2(ts.x, -ts.y));
	vec4 d = texture2D(sTexture, texCoord + vec2(-ts.x, 0.0)) * 2.0;
	vec4 e = texture2D(sTexture, texCoord + vec2(ts.x, 0.0)) * 2.0;
	vec4 f = texture2D(sTexture, texCoord + vec2(-ts.x, ts.y));
	vec4 g = texture2D(sTexture, texCoord + vec2(0.0, ts.y)) * 2.0;
	vec4 h = texture2D(sTexture, texCoord + vec2(ts.x, ts.y));
	vec4 edgex = -a + -d + -f + c + e + h;
	vec4 edgey = a + b + c + -f + -g + -h;
	vec4 sobel = sqrt((edgex * edgex) + (edgey * edgey));
	gl_FragColor = vec4(1.0, 1.0, 1.0, dot(sobel.rgb, vec3(0.299, 0.587, 0.114)));
}

/* Another option:
void main(void) {
	vec2 ts = 1.0 / texSize;
	vec4 px = texture2D(sTexture, texCoord) * 8.0;
	px -= texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y));
	px -= texture2D(sTexture, texCoord + vec2(0.0, -ts.y));
	px -= texture2D(sTexture, texCoord + vec2(ts.x, -ts.y));
	px -= texture2D(sTexture, texCoord + vec2(-ts.x, 0.0));
	px -= texture2D(sTexture, texCoord + vec2(ts.x, 0.0));
	px -= texture2D(sTexture, texCoord + vec2(-ts.x, ts.y));
	px -= texture2D(sTexture, texCoord + vec2(0.0, ts.y));
	px -= texture2D(sTexture, texCoord + vec2(ts.x, ts.y));
	gl_FragColor = vec4(1.0, 1.0, 1.0, dot(px.rgb, vec3(0.299, 0.587, 0.114)));
}
*/