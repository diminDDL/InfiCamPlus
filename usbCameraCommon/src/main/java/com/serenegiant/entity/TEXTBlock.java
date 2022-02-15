package com.serenegiant.entity;

import com.serenegiant.utils.ByteUtil;

/**
 * @author yp2
 * @date 2015-11-18
 * @description tEXt数据块
 */
public class TEXTBlock extends DataBlock{
	
	/**
	 * 1-79 bytes (character string)
	 */
	private byte[] keyword;
	/**
	 * 1 byte (null character)
	 */
	private byte[] nullSeparator;
	/**
	 * 0 or more bytes (character string)
	 */
	private byte[] textString;
	
	public TEXTBlock() {
		super();
		nullSeparator = new byte[1];
	}
	
	public byte[] getKeyword() {
		return keyword;
	}
	public void setKeyword(byte[] keyword) {
		this.keyword = keyword;
	}
	public byte[] getNullSeparator() {
		return nullSeparator;
	}
	public void setNullSeparator(byte[] nullSeparator) {
		this.nullSeparator = nullSeparator;
	}
	public byte[] getTextString() {
		return textString;
	}
	public void setTextString(byte[] textString) {
		this.textString = textString;
	}

	@Override
	public void setData(byte[] data) {
		byte b = 0x00;
		int length = ByteUtil.highByteToInt(this.getLength());
		int pos = 0;
		int index = 0;
		//找到分隔字节所在的位置
		for(int i = 0; i < data.length; i++) {
			if(data[i] == b) {
				index = i;
			}
		}
		//读取keyword
		this.keyword = ByteUtil.cutByte(data, pos, index - 1);
		pos += this.keyword.length;
		//读取nullSeparator
		this.nullSeparator = ByteUtil.cutByte(data, pos, this.nullSeparator.length);
		pos += this.nullSeparator.length;
		//读取textString
		this.textString = ByteUtil.cutByte(data, pos, length - pos);
		pos += this.textString.length;
		
		this.data = data;
	}

}
