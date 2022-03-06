package be.ntmn.inficam;

import static java.lang.Math.PI;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import be.ntmn.libinficam.InfiCam;

public abstract class Palette {
	class Pixel {
		double r, g, b;

		Pixel(double r, double g, double b) {
			this.r = r;
			this.g = g;
			this.b = b;
		}
	}

	abstract Pixel func(double x);

	static public Palette IronBow = new Palette() {
		@Override
		public Pixel func(double x) {
			return new Pixel(sqrt(x), pow(x, 3), max(0.0, sin(2.0 * PI * x)));
		}
	};

	public int[] getData() {
		byte[] palette = new byte[InfiCam.paletteLen * 4];
		for (int i = 0; i + 4 <= palette.length; i += 4) {
			double x = (float) i / (float) palette.length;
			Pixel pixel = func(x);
			palette[i + 0] = (byte) round(255.0 * pixel.r);
			palette[i + 1] = (byte) round(255.0 * pixel.g);
			palette[i + 2] = (byte) round(255.0 * pixel.b);
			palette[i + 3] = (byte) 255;
		}
		IntBuffer ib = ByteBuffer.wrap(palette).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		int[] intPalette = new int[ib.remaining()];
		ib.get(intPalette);
		return intPalette;
	}
}
