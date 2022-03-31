#extension GL_OES_EGL_image_external : require

precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/* This is a modified version of: https://www.shadertoy.com/view/styXDh
 *
 * Some interesting links:
 *   https://vec3.ca/bicubic-filtering-in-fewer-taps/
 *   https://www.shadertoy.com/view/NlBXWR
 *   https://www.decarpentier.nl/2d-catmull-rom-in-4-samples
 *   https://entropymine.com/imageworsener/bicubic/
 *   https://www.shadertoy.com/view/styXDh
 *   https://www.shadertoy.com/view/MllSzX
 *   https://gist.github.com/TheRealMJP/c83b8c0f46b63f3a88a5986f4fa982b1
 */
void main() {
	vec2 Weight[3];
	vec2 Sample[3];

	vec2 UV =  texCoord * texSize;
	vec2 tc = floor(UV - 0.5) + 0.5;
	vec2 f = UV - tc;
	vec2 f2 = f * f;
	vec2 f3 = f2 * f;

	vec2 w0 = f2 - 0.5 * (f3 + f);
	vec2 w1 = 1.5 * f3 - 2.5 * f2 + vec2(1.);
	vec2 w3 = 0.5 * (f3 - f2);
	vec2 w2 = vec2(1.) - w0 - w1 - w3;

	Weight[0] = w0;
	Weight[1] = w1 + w2;
	Weight[2] = w3;

	Sample[0] = (tc - vec2(1.0)) / texSize;
	Sample[1] = (tc + w2 / Weight[1]) / texSize;
	Sample[2] = (tc + vec2(2.0)) / texSize;

	float sampleWeight[5];
	sampleWeight[0] = Weight[1].x * Weight[0].y;
	sampleWeight[1] = Weight[0].x * Weight[1].y;
	sampleWeight[2] = Weight[1].x * Weight[1].y;
	sampleWeight[3] = Weight[2].x * Weight[1].y;
	sampleWeight[4] = Weight[1].x * Weight[2].y;

	vec3 Ct = texture2D(sTexture, vec2(Sample[1].x, Sample[0].y)).rgb * sampleWeight[0];
	vec3 Cl = texture2D(sTexture, vec2(Sample[0].x, Sample[1].y)).rgb * sampleWeight[1];
	vec3 Cc = texture2D(sTexture, vec2(Sample[1].x, Sample[1].y)).rgb * sampleWeight[2];
	vec3 Cr = texture2D(sTexture, vec2(Sample[2].x, Sample[1].y)).rgb * sampleWeight[3];
	vec3 Cb = texture2D(sTexture, vec2(Sample[1].x, Sample[2].y)).rgb * sampleWeight[4];

	float WeightMultiplier = 1.0 / (sampleWeight[0] + sampleWeight[1] + sampleWeight[2] +
		sampleWeight[3] + sampleWeight[4]);

	gl_FragColor = vec4((Ct + Cl + Cc + Cr + Cb) * WeightMultiplier, 1.0);
}
