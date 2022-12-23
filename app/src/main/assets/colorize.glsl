#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

void main(void) {
	// TODO make this actually do something
	gl_FragColor = texture2D(sTexture, texCoord);
}
