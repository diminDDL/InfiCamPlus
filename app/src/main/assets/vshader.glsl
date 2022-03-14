attribute vec2 vPosition;
attribute vec2 vTexCoord;
varying vec2 texCoord;
uniform vec2 scale;
uniform vec2 translate;

void main(void) {
	texCoord = vTexCoord;
	vec2 pos = vPosition * scale + translate;
	gl_Position = vec4(pos.x, pos.y, 0.0, 1.0);
}
