package be.ntmn.inficam;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import android.graphics.Bitmap;


public abstract class Palette {

	public static final int paletteSize = 65535;
	private static final int bitmapHeight = 512;

	public int name;

	public Palette(int name) { this.name = name; }

	public static class Pixel {

		double r, g, b, a;

		Pixel(double r, double g, double b, double a) {
			this.r = r;
			this.g = g;
			this.b = b;
			this.a = a;
		}
	}
	public static class ScaledPixel {

		int r, g, b, a;

		ScaledPixel(Pixel pixel) {
			this.r = (int)round(pixel.r*255);
			this.g = (int)round(pixel.g*255);
			this.b = (int)round(pixel.b*255);
			this.a = (int)round(pixel.a*255);
		}
	}

	abstract Pixel func(double x);

	private static final Palette WhiteHot = new Palette(R.string.palette_whitehot) {
		@Override
		public Pixel func(double x) {
			return new Pixel(x, x, x, 1);
		}
	};

	private static final Palette BlackHot = new Palette(R.string.palette_blackhot) {
		@Override
		public Pixel func(double x) {
			return new Pixel(1 - x, 1 - x, 1 - x, 1);
		}
	};

	private static final Palette RedHot = new Palette(R.string.palette_redhot) {
		@Override
		public Pixel func(double x) {
			return new Pixel(x, 0, 0, 1);
		}
	};

	private static final Palette RedCold = new Palette(R.string.palette_redcold) {
		@Override
		public Pixel func(double x) {
			return new Pixel(1 - x, 0, 0, 1);
		}
	};

	private static final Palette GreenHot = new Palette(R.string.palette_greenhot) {
		@Override
		public Pixel func(double x) {
			return new Pixel(0, x, 0, 1);
		}
	};

	private static final Palette GreenCold = new Palette(R.string.palette_greencold) {
		@Override
		public Pixel func(double x) {
			return new Pixel(0, 1 - x, 0, 1);
		}
	};

	private static final Palette Ironbow = new Palette(R.string.palette_ironbow) {
		@Override
		public Pixel func(double x) {
			return new Pixel(sqrt(x), pow(x, 3), max(0.0, sin(2.0 * PI * x)), 1);
		}
	};

	private static final Palette Rainbow = new Palette(R.string.palette_rainbow) {
		@Override
		Pixel func(double x) {
			return hsvPixel((1 - x) * 360.0, 1, 1);
		}
	};

	private static final Palette Rainbow2 = new Palette(R.string.palette_rainbow2) {
		@Override
		Pixel func(double x) {
			return hsvPixel((1 - x) * 270.0, 1, 1);
		}
	};

	public static Palette[] palettes = new Palette[] {
			WhiteHot, BlackHot, RedHot, RedCold, GreenHot, GreenCold, Ironbow, Rainbow, Rainbow2
	};

	private static Pixel hsvPixel(double h, double s, double v) {
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
		return new Pixel(r + m, g + m, b + m, 1);
	}

	public int[] getMap() {
		int[] palette = new int[paletteSize];
		for (int i = 0; i < palette.length; i++) {
			double x = (float) i / (float) palette.length;
			ScaledPixel pix = new ScaledPixel(func(x));
			palette[i] = (pix.a << 24) |
						 (pix.r << 16) |
						 (pix.g <<	8) |
						 (pix.b);
		}
		return palette;
	}

	public Bitmap getBitmap() {
		Bitmap bitmap = Bitmap.createBitmap(
			1,
			bitmapHeight,
			Bitmap.Config.ARGB_8888
		);
		int[] map = getMap();
		int[] rawArray = new int[bitmapHeight];
		for (int y = 0; y < rawArray.length; ++y) {
			float pos = 1.0f - (float)y / (float)(rawArray.length - 1);
			int mapIndex = (int)(pos * (map.length - 1) + 0.5f);
			rawArray[y] = map[mapIndex];
		}
		bitmap.setPixels(rawArray, 0, 1, 0, 0, 1, bitmapHeight);
		return bitmap;
	}
}
