package com.serenegiant.entity;

/**
 * @author yp2
 * @date 2015-11-18
 * @description 抽象数据块
 */
public abstract class DataBlock {
	
	/**
	 * 指定数据块中数据域的长度，4个字节
	 */
	private byte[] length;
	/**
	 * 数据块类型码由ASCII字母(A-Z和a-z)组成，4个字节
	 */
	private byte[] chunkTypeCode;
	/**
	 * 数据块数据
	 */
	protected byte[] data;
	/**
	 * 存储用来检测是否有错误的循环冗余码，4个字节
	 */
	private byte[] crc;
	
	public DataBlock() {
		length = new byte[4];
		chunkTypeCode = new byte[4];
		crc = new byte[4];
		data = null;
	}
	
	public byte[] getLength() {
		return length;
	}
	public void setLength(byte[] length) {
		this.length = length;
	}
	public byte[] getChunkTypeCode() {
		return chunkTypeCode;
	}
	public void setChunkTypeCode(byte[] chunkTypeCode) {
		this.chunkTypeCode = chunkTypeCode;
	}
	public byte[] getData() {
		return data;
	}
	public byte[] getCrc() {
		return crc;
	}
	public void setCrc(byte[] crc) {
		this.crc = crc;
	}
	
	public abstract void setData(byte[] data);
}
