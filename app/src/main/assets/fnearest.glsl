#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

void main(void) {
	gl_FragColor = texture2D(sTexture, (floor(texCoord * texSize) + 0.5) / texSize);
}
