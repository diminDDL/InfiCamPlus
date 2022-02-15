package com.serenegiant.entity;

import com.serenegiant.utils.ByteUtil;

/**
 * @author yp2
 * @date 2015-11-18
 * @description IHDR数据块
 */
public class IHDRBlock extends DataBlock{
	
	/**
	 * 图像宽度，以像素为单位，4个字节
	 */
	private byte[] width;
	/**
	 * 图像高度，以像素为单位，4个字节
	 */
	private byte[] height;
	/**
	 * 图像深度，1个字节
	 */
	private byte[] bitDepth;
	/**
	 * 颜色类型，1个字节
	 */
	private byte[] colourType;
	/**
	 * 压缩方法(LZ77派生算法)，1个字节
	 */
	private byte[] compressionMethod;
	/**
	 * 滤波器方法，1个字节
	 */
	private byte[] filterMethod;
	/**
	 * 隔行扫描方法，1个字节
	 */
	private byte[] interlaceMethod;
	
	public IHDRBlock() {
		super();
		width = new byte[4];
		height = new byte[4];
		bitDepth = new byte[1];
		colourType = new byte[1];
		compressionMethod = new byte[1];
		filterMethod = new byte[1];
		interlaceMethod = new byte[1];
	}
	
	public byte[] getWidth() {
		return width;
	}
	public void setWidth(byte[] width) {
		this.width = width;
	}
	public byte[] getHeight() {
		return height;
	}
	public void setHeight(byte[] height) {
		this.height = height;
	}
	public byte[] getBitDepth() {
		return bitDepth;
	}
	public void setBitDepth(byte[] bitDepth) {
		this.bitDepth = bitDepth;
	}
	public byte[] getColourType() {
		return colourType;
	}
	public void setColourType(byte[] colourType) {
		this.colourType = colourType;
	}
	public byte[] getCompressionMethod() {
		return compressionMethod;
	}
	public void setCompressionMethod(byte[] compressionMethod) {
		this.compressionMethod = compressionMethod;
	}
	public byte[] getFilterMethod() {
		return filterMethod;
	}
	public void setFilterMethod(byte[] filterMethod) {
		this.filterMethod = filterMethod;
	}
	public byte[] getInterlaceMethod() {
		return interlaceMethod;
	}
	public void setInterlaceMethod(byte[] interlaceMethod) {
		this.interlaceMethod = interlaceMethod;
	}

	@Override
	public void setData(byte[] data) {
		int pos = 0;
		this.width = ByteUtil.cutByte(data, pos, this.width.length);
		pos += this.width.length;
		this.height = ByteUtil.cutByte(data, pos, this.height.length);
		pos += this.height.length;
		this.bitDepth = ByteUtil.cutByte(data, pos, this.bitDepth.length);
		pos += this.bitDepth.length;
		this.colourType = ByteUtil.cutByte(data, pos, this.colourType.length);
		pos += this.colourType.length;
		this.compressionMethod = ByteUtil.cutByte(data, pos, this.compressionMethod.length);
		pos += this.compressionMethod.length;
		this.filterMethod = ByteUtil.cutByte(data, pos, this.filterMethod.length);
		pos += this.filterMethod.length;
		this.interlaceMethod = ByteUtil.cutByte(data, pos, this.interlaceMethod.length);
		pos += this.interlaceMethod.length;
		
		this.data = data;
	}

}
