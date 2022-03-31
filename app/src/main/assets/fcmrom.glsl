#extension GL_OES_EGL_image_external : require

precision highp float; // TODO mediump
uniform samplerExternalOES sTexture;
varying vec2 texCoord;
uniform vec2 texSize;

/* This code is a modified version of the following:
 *   https://gist.github.com/TheRealMJP/c83b8c0f46b63f3a88a5986f4fa982b1
 *
 * Some interesting links:
 *   https://vec3.ca/bicubic-filtering-in-fewer-taps/
 *   https://www.shadertoy.com/view/NlBXWR
 *   https://www.decarpentier.nl/2d-catmull-rom-in-4-samples
 *   https://entropymine.com/imageworsener/bicubic/
 *   https://www.shadertoy.com/view/styXDh
 *   https://www.shadertoy.com/view/MllSzX
 *
 * TODO mention third party license in license.txt
 *
 * MIT License
 * Copyright (c) 2019 MJP
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
void main(void) {
	vec2 uv = texCoord * texSize - 0.5; /* Center of the closest texel in bitmap coords. */
	vec2 iuv = floor(uv) + 0.5; /* Top left of that pixel. */
	vec2 fuv = fract(uv); /* How far off the top left of that we are. */

	/*vec2 w0 = fuv * (-0.5 + fuv * (1.0 - 0.5 * fuv));
	vec2 w1 = 1.0f + fuv * fuv * (-2.5 + 1.5 * fuv);
	vec2 w2 = fuv * (0.5 + fuv * (2.0 - 1.5 * fuv));
	vec2 w3 = fuv * fuv * (-0.5 + 0.5 * fuv);

	vec2 w12 = w1 + w2;
	vec2 offset12 = w2 / (w1 + w2);

	vec2 texPos0 = (iuv - 1.0) / texSize;
	vec2 texPos3 = (iuv + 2.0) / texSize;
	vec2 texPos12 = (iuv + offset12) / texSize;

	vec4 result = vec4(0.0);

	result += texture2D(sTexture, vec2(texPos0.x, texPos0.y)) * w0.x * w0.y;
	result += texture2D(sTexture, vec2(texPos12.x, texPos0.y)) * w12.x * w0.y;
	result += texture2D(sTexture, vec2(texPos3.x, texPos0.y)) * w3.x * w0.y;

	result += texture2D(sTexture, vec2(texPos0.x, texPos12.y)) * w0.x * w12.y;
	result += texture2D(sTexture, vec2(texPos12.x, texPos12.y)) * w12.x * w12.y;
	result += texture2D(sTexture, vec2(texPos3.x, texPos12.y)) * w3.x * w12.y;

	result += texture2D(sTexture, vec2(texPos0.x, texPos3.y)) * w0.x * w3.y;
	result += texture2D(sTexture, vec2(texPos12.x, texPos3.y)) * w12.x * w3.y;
	result += texture2D(sTexture, vec2(texPos3.x, texPos3.y)) * w3.x * w3.y;

	result.w = 1.0;
	gl_FragColor = result;*/





	// TODO this one is from https://www.shadertoy.com/view/styXDh

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

	Sample[0] = tc - vec2(1.);
	Sample[1] = tc + w2 / Weight[1];
	Sample[2] = tc + vec2(2.);

	Sample[0] /= texSize;
	Sample[1] /= texSize;
	Sample[2] /= texSize;

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

	float WeightMultiplier = 1./(sampleWeight[0]+sampleWeight[1]+sampleWeight[2]+sampleWeight[3]+sampleWeight[4]);


	gl_FragColor = vec4((Ct+Cl+Cc+Cr+Cb)*WeightMultiplier, 1.0);
}
