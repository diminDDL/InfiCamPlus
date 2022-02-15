package com.serenegiant.factory;

import java.io.IOException;
import java.io.InputStream;

import com.serenegiant.entity.DataBlock;
import com.serenegiant.entity.IDATBlock;
import com.serenegiant.entity.IENDBlock;
import com.serenegiant.entity.IHDRBlock;
import com.serenegiant.entity.PHYSBlock;
import com.serenegiant.entity.PLTEBlock;
import com.serenegiant.entity.Png;
import com.serenegiant.entity.SRGBBlock;
import com.serenegiant.entity.TEXTBlock;
import com.serenegiant.entity.TRNSBlock;
import com.serenegiant.utils.BlockUtil;
import com.serenegiant.utils.ByteUtil;

public class BlockFactory {
	
	public static DataBlock readBlock(InputStream in, Png png, DataBlock dataBlock) throws IOException {
		String hexCode = ByteUtil.byteToHex(dataBlock.getChunkTypeCode(), 
								0, dataBlock.getChunkTypeCode().length);
		hexCode = hexCode.toUpperCase();
		DataBlock realDataBlock = null;
		if(BlockUtil.isIHDR(hexCode)) {
			//IHDR数据块
			realDataBlock = new IHDRBlock();
		} else if(BlockUtil.isPLTE(hexCode)) {
			//PLTE数据块
			realDataBlock = new PLTEBlock();
		} else if(BlockUtil.isIDAT(hexCode)) {
			//IDAT数据块
			realDataBlock = new IDATBlock();
		} else if(BlockUtil.isIEND(hexCode)) {
			//IEND数据块
			realDataBlock = new IENDBlock();
		} else if(BlockUtil.isSRGB(hexCode)) {
			//sRGB数据块
			realDataBlock = new SRGBBlock();
		} else if(BlockUtil.istEXt(hexCode)) {
			//tEXt数据块
			realDataBlock = new TEXTBlock();
		} else if(BlockUtil.isPHYS(hexCode)) {
			//pHYs数据块
			realDataBlock = new PHYSBlock();
		} else if(BlockUtil.istRNS(hexCode)) {
			//tRNS数据块
			realDataBlock = new TRNSBlock();
		} else {
			//其它数据块
			realDataBlock = dataBlock;
		}
		realDataBlock.setLength(dataBlock.getLength());
		realDataBlock.setChunkTypeCode(dataBlock.getChunkTypeCode());
		//读取数据,这里的测试版做法是： 把所有数据读取进内存来
		int len = -1;
		int dataLength=ByteUtil.highByteToInt(dataBlock.getLength());
		byte[] data = new byte[dataLength];
		len = in.read(data, 0, dataLength);
		realDataBlock.setData(ByteUtil.cutByte(data, 0, len));
		return realDataBlock;
	}
	
}
