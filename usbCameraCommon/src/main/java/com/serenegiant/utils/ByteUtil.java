package com.serenegiant.utils;

/**
 * @author yp2
 * @date 2015-11-18
 * @description 字节操作工具
 */
public class ByteUtil {

	/**
	 * 将byte数组转换为16进制字符串
	 * <br/>
	 * 实现思路：
	 * 先将byte转换成int,再使用Integer.toHexString(int)
	 * @param data	byte数组
	 * @return
	 */
	public static String byteToHex(byte[] data, int start, int end) {
		StringBuilder builder = new StringBuilder();
		for(int i = start; i < end; i++) {
			int tmp = data[i] & 0xff;
			String hv = Integer.toHexString(tmp);
			if(hv.length() < 2) {
				builder.append("0");
			}
			builder.append(hv);
			/*builder.append(" ");*/
			if(i % 16 == 15) {
				/*builder.append("\n");*/
			}
		}
		return builder.toString();
	}
	
	/**
	 * 将byte数组转换为16进制字符串(该字符串方便查看)
	 * 输出信息版：16个字节一行显示
	 * @param data
	 * @param start
	 * @param end
	 * @return
	 */
	public static String byteToHexforPrint(byte[] data, int start, int end) {
		StringBuilder builder = new StringBuilder();
		for(int i = start; i < end; i++) {
			int tmp = data[i] & 0xff;
			String hv = Integer.toHexString(tmp);
			if(hv.length() < 2) {
				builder.append("0");
			}
			builder.append(hv);
			builder.append(" ");
			if(i % 16 == 15) {
				builder.append("\n");
			}
		}
		return builder.toString();
	}
	
	/**
	 * 十六进制字符串转换为字节数组
	 * @param hexStr	十六进制字符串
	 * @return			字节数组
	 */
	public static byte[] hexToByte(String hexStr) {
		byte[] datas = new byte[(hexStr.length() - 1) / 2 + 1];
		hexStr = hexStr.toUpperCase();
		int pos = 0;
		for(int i = 0; i < hexStr.length(); i+=2) {
			if(i + 1 < hexStr.length()) {
				datas[pos] = (byte) ((indexOf(hexStr.charAt(i)+"") << 4) + indexOf(hexStr.charAt(i+1)+""));
			}
			pos++;
		}
		return datas;
	}
	
	/**
	 * 计算指定字符串（这里要求是字符）的16进制所表示的数字
	 * @param str
	 * @return
	 */
	public static int indexOf(String str) {
		return "0123456789ABCDEF".indexOf(str);
	}
	
	/**
	 * 计算byte数组所表示的值，字节数组的值以小端表示，低位在低索引上，高位在高索引
	 * <br/>
	 * 例：data = {1,2},那么结果为: 2 << 8 + 1 = 513
	 * @param data	byte数组
	 * @return		计算出的值
	 */
	public static long lowByteToLong(byte[] data) {
		long sum = 0;
		for(int i = 0; i < data.length; i++) {
			long value = ((data[i] & 0xff) << (8 * i));
			sum += value;
		}
		return sum;
	}
	
	/**
	 * 计算byte数组所表示的值，字节数组的值以大端表示，低位在高索引上，高位在低索引
	 * <br/>
	 * 例：data = {1,2},那么结果为: 1 << 8 + 2 = 258
	 * @param data	byte数组
	 * @return		计算出的值
	 */
	public static long highByteToLong(byte[] data) {
		long sum = 0;
		for(int i = 0; i < data.length; i++) {
			long value = ((data[i] & 0xff) << (8 * (data.length - i - 1)));
			sum += value;
		}
		return sum;
	}
	
	/**
	 * 计算byte数组所表示的值，字节数组的值以小端表示，低位在低索引上，高位在高索引
	 * <br/>
	 * 例：data = {1,2},那么结果为: 2 << 8 + 1 = 513
	 * @param data	byte数组
	 * @return		计算出的值
	 */
	public static int lowByteToInt(byte[] data) {
		int sum = 0;
		for(int i = 0; i < data.length; i++) {
			long value = ((data[i] & 0xff) << (8 * i));
			sum += value;
		}
		return sum;
	}
	
	/**
	 * 计算byte数组所表示的值，字节数组的值以大端表示，低位在高索引上，高位在低索引
	 * <br/>
	 * 例：data = {1,2},那么结果为: 1 << 8 + 2 = 258
	 * @param data	byte数组
	 * @return		计算出的值
	 */
	public static int highByteToInt(byte[] data) {
		int sum = 0;
		for(int i = 0; i < data.length; i++) {
			long value = ((data[i] & 0xff) << (8 * (data.length - i - 1)));
			sum += value;
		}
		return sum;
	}
	
	/**
	 * long值转换为指定长度的小端字节数组
	 * @param data		long值
	 * @param len		长度
	 * @return			字节数组,小端形式展示
	 */
	public static byte[] longToLowByte(long data, int len) {
		byte[] value = new byte[len];
		for(int i = 0; i < len; i++) {
			value[i] = (byte) ((data >> (8 * i )) & 0xff);
		}
		return value;
	}
	
	/**
	 * long值转换为指定长度的大端字节数组
	 * @param data		long值
	 * @param len		长度
	 * @return			字节数组,大端形式展示
	 */
	public static byte[] longToHighByte(long data, int len) {
		byte[] value = new byte[len];
		for(int i = 0; i < len; i++) {
			value[i] = (byte) ((data >> (8 * (len - 1 - i) )) & 0xff);
		}
		return value;
	}
	
	/**
	 * int值转换为指定长度的小端字节数组
	 * @param data		int值
	 * @param len		长度
	 * @return			字节数组,小端形式展示
	 */
	public static byte[] intToLowByte(int data, int len) {
		byte[] value = new byte[len];
		for(int i = 0; i < len; i++) {
			value[i] = (byte) ((data >> (8 * i )) & 0xff);
		}
		return value;
	}
	
	/**
	 * int值转换为指定长度的大端字节数组
	 * @param data		int值
	 * @param len		长度
	 * @return			字节数组,大端形式展示
	 */
	public static byte[] intToHighByte(int data, int len) {
		byte[] value = new byte[len];
		for(int i = 0; i < len; i++) {
			value[i] = (byte) ((data >> (8 * (len - 1 - i) )) & 0xff);
		}
		return value;
	}
	
	/**
	 * 计算base的exponent次方
	 * @param base  	基数
	 * @param exponent	指数
	 * @return
	 */
	public static long power(int base, int exponent) {
		long sum = 1;
		for(int i = 0; i < exponent; i++) {
			sum *= base;
		}
		return sum;
	}
	
	/**
	 * 裁剪字节数据，获取指定开始位置（0开始）后的第个len字节
	 * @param data		原来的字节数组
	 * @param start		开始位置
	 * @param len		长度
	 * @return			裁剪后的字节数组
	 */
	public static byte[] cutByte(byte[] data, int start, int len) {
		byte[] value = null;
		do {
			if(len + start > data.length || start < 0 || len <= 0) {
				break;
			}
			value = new byte[len];
			for(int i = 0; i < len; i++) {
				value[i] = data[start + i];
			}
		} while (false);
		
		return value;
	}
	/**
	 * 转换short为byte
	 *
	 * @param b
	 * @param s
	 *            需要转换的short
	 * @param index
	 */
	public static void putShort(byte b[], short s, int index) {
		b[index + 1] = (byte) (s >> 8);
		b[index + 0] = (byte) (s >> 0);
	}

	/**
	 * 通过byte数组取到short
	 *
	 * @param b
	 * @param index
	 *            第几位开始取
	 * @return
	 */
	public static short getShort(byte[] b, int index) {
		return (short) (((b[index + 1] << 8) | b[index + 0] & 0xff));
	}

	/**
	 * 转换int为byte数组
	 *
	 * @param bb
	 * @param x
	 * @param index
	 */
	public static void putInt(byte[] bb, int x, int index) {
		bb[index + 3] = (byte) (x >> 24);
		bb[index + 2] = (byte) (x >> 16);
		bb[index + 1] = (byte) (x >> 8);
		bb[index + 0] = (byte) (x >> 0);
	}

	/**
	 * 通过byte数组取到int
	 *
	 * @param bb
	 * @param index
	 *            第几位开始
	 * @return
	 */
	public static int getInt(byte[] bb, int index) {
		return (int) ((((bb[index + 3] & 0xff) << 24)
				| ((bb[index + 2] & 0xff) << 16)
				| ((bb[index + 1] & 0xff) << 8) | ((bb[index + 0] & 0xff) << 0)));
	}

	/**
	 * 转换long型为byte数组
	 *
	 * @param bb
	 * @param x
	 * @param index
	 */
	public static void putLong(byte[] bb, long x, int index) {
		bb[index + 7] = (byte) (x >> 56);
		bb[index + 6] = (byte) (x >> 48);
		bb[index + 5] = (byte) (x >> 40);
		bb[index + 4] = (byte) (x >> 32);
		bb[index + 3] = (byte) (x >> 24);
		bb[index + 2] = (byte) (x >> 16);
		bb[index + 1] = (byte) (x >> 8);
		bb[index + 0] = (byte) (x >> 0);
	}

	/**
	 * 通过byte数组取到long
	 *
	 * @param bb
	 * @param index
	 * @return
	 */
	public static long getLong(byte[] bb, int index) {
		return ((((long) bb[index + 7] & 0xff) << 56)
				| (((long) bb[index + 6] & 0xff) << 48)
				| (((long) bb[index + 5] & 0xff) << 40)
				| (((long) bb[index + 4] & 0xff) << 32)
				| (((long) bb[index + 3] & 0xff) << 24)
				| (((long) bb[index + 2] & 0xff) << 16)
				| (((long) bb[index + 1] & 0xff) << 8) | (((long) bb[index + 0] & 0xff) << 0));
	}

	/**
	 * 字符到字节转换
	 *
	 * @param ch
	 * @return
	 */
	public static void putChar(byte[] bb, char ch, int index) {
		int temp = (int) ch;
		// byte[] b = new byte[2];
		for (int i = 0; i < 2; i ++ ) {
			bb[index + i] = new Integer(temp & 0xff).byteValue(); // 将最高位保存在最低位
			temp = temp >> 8; // 向右移8位
		}
	}

	/**
	 * 字节到字符转换
	 *
	 * @param b
	 * @return
	 */
	public static char getChar(byte[] b, int index) {
		int s = 0;
		if (b[index + 1] > 0)
			s += b[index + 1];
		else
			s += 256 + b[index + 0];
		s *= 256;
		if (b[index + 0] > 0)
			s += b[index + 1];
		else
			s += 256 + b[index + 0];
		char ch = (char) s;
		return ch;
	}

	/**
	 * float转换byte
	 *
	 * @param bb
	 * @param x
	 * @param index
	 */
	public static void putFloat(byte[] bb, float x, int index) {
		// byte[] b = new byte[4];
		int l = Float.floatToIntBits(x);
		for (int i = 0; i < 4; i++) {
			bb[index + i] = new Integer(l).byteValue();
			l = l >> 8;
		}
	}

	/**
	 * 通过byte数组取得float
	 *
	 * @param b
	 * @param index
	 * @return
	 */
	public static float getFloat(byte[] b, int index) {
		int l;
		l = b[index + 0];
		l &= 0xff;
		l |= ((long) b[index + 1] << 8);
		l &= 0xffff;
		l |= ((long) b[index + 2] << 16);
		l &= 0xffffff;
		l |= ((long) b[index + 3] << 24);
		return Float.intBitsToFloat(l);
	}

	/**
	 * double转换byte
	 *
	 * @param bb
	 * @param x
	 * @param index
	 */
	public static void putDouble(byte[] bb, double x, int index) {
		// byte[] b = new byte[8];
		long l = Double.doubleToLongBits(x);
		for (int i = 0; i < 4; i++) {
			bb[index + i] = new Long(l).byteValue();
			l = l >> 8;
		}
	}

	/**
	 * 通过byte数组取得float
	 *
	 * @param b
	 * @param index
	 * @return
	 */
	public static double getDouble(byte[] b, int index) {
		long l;
		l = b[0];
		l &= 0xff;
		l |= ((long) b[1] << 8);
		l &= 0xffff;
		l |= ((long) b[2] << 16);
		l &= 0xffffff;
		l |= ((long) b[3] << 24);
		l &= 0xffffffffl;
		l |= ((long) b[4] << 32);
		l &= 0xffffffffffl;
		l |= ((long) b[5] << 40);
		l &= 0xffffffffffffl;
		l |= ((long) b[6] << 48);
		l &= 0xffffffffffffffl;
		l |= ((long) b[7] << 56);
		return Double.longBitsToDouble(l);
	}
	
	public static void main(String[] args) {
		byte[] data = new byte[]{1,2};
		System.out.println(highByteToInt(data));
		System.out.println(lowByteToInt(data));
		System.out.println(byteToHex(intToHighByte(258, 4), 0, 4));
		System.out.println(byteToHex(intToLowByte(258, 4), 0, 4));
	}
}
