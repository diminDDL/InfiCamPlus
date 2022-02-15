package com.serenegiant.utils;


public class BlockUtil {
	
	public static boolean isIHDR(String hexCode) {
		boolean isIhdr = false;
		if("49484452".equals(hexCode)) {
			isIhdr = true;
		}
		return isIhdr;
	}
	
	public static boolean isPLTE(String hexCode) {
		boolean isPlte = false;
		if("504C5445".equals(hexCode)) {
			isPlte = true;
		}
		return isPlte;
	}
	
	public static boolean isIDAT(String hexCode) {
		boolean isIdat = false;
		if("49444154".equals(hexCode)) {
			isIdat = true;
		}
		return isIdat;
	}

	public static boolean isIEND(String hexCode) {
		boolean isIend = false;
		if("49454E44".equals(hexCode)) {
			isIend = true;
		}
		return isIend;
	}
	
	public static boolean isSRGB(String hexCode) {
		boolean isSrgb = false;
		if("73524742".equals(hexCode)) {
			isSrgb = true;
		}
		return isSrgb;
	}
	
	public static boolean istEXt(String hexCode) {
		boolean istEXt = false;
		if("74455874".equals(hexCode)) {
			istEXt = true;
		}
		return istEXt;
	}
	
	public static boolean isPHYS(String hexCode) {
		boolean isPhys = false;
		if("70485973".equals(hexCode)) {
			isPhys = true;
		}
		return isPhys;
	}
	
	public static boolean istRNS(String hexCode) {
		boolean istRNS = false;
		if("74524E53".equals(hexCode)) {
			istRNS = true;
		}
		return istRNS;
	}
}
