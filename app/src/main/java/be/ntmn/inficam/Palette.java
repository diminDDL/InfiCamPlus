package be.ntmn.inficam;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
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
	public String name;

	Palette(String name) {
		this.name = name;
	}

	static class Pixel {
		double r, g, b;

		Pixel(double r, double g, double b) {
			this.r = r;
			this.g = g;
			this.b = b;
		}
	}

	abstract Pixel func(double x);

	static public Palette WhiteHot = new Palette("White Hot") {
		@Override
		public Pixel func(double x) {
			return new Pixel(x, x, x);
		}
	};

	static public Palette BlackHot = new Palette("Black Hot") {
		@Override
		public Pixel func(double x) {
			return new Pixel(1 - x, 1 - x, 1 - x);
		}
	};

	static public Palette Ironbow = new Palette("Ironbow") {
		@Override
		public Pixel func(double x) {
			return new Pixel(sqrt(x), pow(x, 3), max(0.0, sin(2.0 * PI * x)));
		}
	};

	static public Palette Rainbow = new Palette("Rainbow") {
		@Override
		Pixel func(double x) {
			return hsvPixel((1 - x) * 360.0, 1, 1);
		}
	};

	static public Palette Rainbow2 = new Palette("Rainbow 2") {
		@Override
		Pixel func(double x) {
			return hsvPixel((1 - x) * 270.0, 1, 1);
		}
	};

	public static Palette[] palettes = new Palette[] {
			WhiteHot, BlackHot, Ironbow, Rainbow, Rainbow2
	};

	Pixel hsvPixel(double h, double s, double v) {
		double r, g, b;
		double c = s * v;
		double y = c * (1 - abs((h / 60.0) % 2 - 1));
		double m = v - c;
		if (h >= 0 && h < 60) {
			r = c; g = y; b = 0;
		} else if (h >= 60 && h < 120) {
			r = y; g = c; b = 0;
		} else if (h >= 120 && h < 180) {
			r = 0; g = c; b = y;
		} else if (h >= 180 && h < 240) {
			r = 0; g = y; b = c;
		} else if(h >= 240 && h < 300) {
			r = y; g = 0; b = c;
		} else {
			r = c; g = 0; b = y;
		}
		return new Pixel(r + m, g + m, b + m);
	}

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
