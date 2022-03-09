attribute vec2 vPosition;
attribute vec2 vTexCoord;
varying vec2 texCoord;

void main() {
	texCoord = vTexCoord;
	// TODO maybe we can flip vposition to rotate for picture taking?
	//   either way if we ever need rotating, we can do it here
	gl_Position = vec4(vPosition.x, vPosition.y, 0.0, 1.0);
}
