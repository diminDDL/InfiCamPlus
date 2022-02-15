package com.serenegiant.entity;

/**
 * @author yp2
 * @date 2015-11-18
 * @description IDAT数据块
 */
public class IDATBlock extends DataBlock{

	@Override
	public void setData(byte[] data) {
		this.data = data;
	}

}
