#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;
uniform float sharpening;

void sort2(inout vec4 a0, inout vec4 a1) {
	vec4 b0 = min(a0, a1);
	vec4 b1 = max(a0, a1);
	a0 = b0;
	a1 = b1;
}

vec4 med(in vec4 a0, in vec4 a1, in vec4 a2) {
	sort2(a0, a1);
	sort2(a0, a2);
	sort2(a1, a2);
	return a1;
}

void main(void) { /* Awesome page: https://setosa.io/ev/image-kernels/ */
	vec4 px = texture2D(sTexture, texCoord);
	if (sharpening > 0.0) {
		vec2 ts = 1.0 / texSize;
		vec4 v[9];
		v[0] = px;
		v[1] = texture2D(sTexture, texCoord + vec2(0.0, -ts.y));
		v[2] = texture2D(sTexture, texCoord + vec2(0.0, ts.y));
		v[3] = texture2D(sTexture, texCoord + vec2(-ts.x, 0));
		v[4] = texture2D(sTexture, texCoord + vec2(ts.x, 0));

		v[5] = texture2D(sTexture, texCoord + vec2(-ts.x, -ts.y));
		v[6] = texture2D(sTexture, texCoord + vec2(ts.x, -ts.y));
		v[7] = texture2D(sTexture, texCoord + vec2(-ts.x, ts.y));
		v[8] = texture2D(sTexture, texCoord + vec2(ts.x, ts.y));

		int i, j;
		vec4 temp;
		for(i=0 ; i < v.length(); i++) {
			for(j = 0 ; j < v.length() - 1; j++) {
				if(v[j].y > v[j+1].y) {
					temp        = v[j];
					v[j]    = v[j+1];
					v[j+1]  = temp;
				}
			}
		}

		vec4 spx = px + (px - v[4]);
		px = mix(px, spx, sharpening);
	}
	gl_FragColor = px;
}
