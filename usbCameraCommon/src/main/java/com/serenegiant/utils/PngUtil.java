package com.serenegiant.utils;

import android.animation.FloatArrayEvaluator;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;

import com.serenegiant.entity.CommonBlock;
import com.serenegiant.entity.DataBlock;
import com.serenegiant.entity.HISTBlock;
import com.serenegiant.entity.Png;
import com.serenegiant.entity.PngHeader;
import com.serenegiant.factory.BlockFactory;

/**
 * @author yp2
 * @date 2015-11-19
 * @decription 隐藏文件内容到png格式图片中
 */
public class PngUtil {
	
	/**
	 * 读取指定png文件的信息
	 * @param pngFileName
	 * @return
	 * @throws IOException 
	 */
	private static Png readPng(String pngFileName) throws IOException {
		Png png = new Png();
		File pngFile = new File(pngFileName);
		InputStream pngIn = null;
		//记录输入流读取位置(字节为单位)
		long pos = 0;
		try {
			pngIn = new FileInputStream(pngFile);
			//读取头部信息
			PngHeader pngHeader = new PngHeader();
			pngIn.read(pngHeader.getFlag());
			png.setPngHeader(pngHeader);
			pos += pngHeader.getFlag().length;
			
			while(pos < pngFile.length()) {
				DataBlock realDataBlock = null;
				//读取数据块
				DataBlock dataBlock = new CommonBlock();
				//先读取长度，4个字节
				pngIn.read(dataBlock.getLength());
				pos += dataBlock.getLength().length;
				//再读取类型码，4个字节
				pngIn.read(dataBlock.getChunkTypeCode());
				pos += dataBlock.getChunkTypeCode().length;
				//如果有数据再读取数据
				//读取数据
				realDataBlock = BlockFactory.readBlock(pngIn, png, dataBlock);
				pos += ByteUtil.highByteToInt(dataBlock.getLength());
				//读取crc，4个字节
				pngIn.read(realDataBlock.getCrc());
				//添加读取到的数据块
				png.getDataBlocks().add(realDataBlock);
				pos += realDataBlock.getCrc().length;
				dataBlock = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {
				if(pngIn != null) {
					pngIn.close();
				}
			} catch (IOException e) {
				Log.e("readPng", "handleUpdateMedia wirteByteArrayToPng:", e);
				throw e;
			}
		}
		return png;
	}
	
	/**
	 * 将读取到的文件信息写入到指定png的文件中，并指定输出文件
	 * @param png				Png信息对象
	 * @param pngFileName		png文件名
	 * @param inputFileName		要隐藏的文件名
	 * @param outFileName		输出文件名，内容包括png数据和要隐藏文件的信息
	 * @throws IOException 
	 */
	private static void wirteFileToPng(Png png, String pngFileName, String inputFileName, String outFileName) throws IOException {
		File pngFile = new File(pngFileName);
		File inputFile = new File(inputFileName);
		File outFile = new File(outFileName);
		InputStream pngIn = null;
		InputStream inputIn = null;
		OutputStream out = null;
		int len = -1;
		byte[] buf = new byte[1024];
		try {
			if(!outFile.exists()) {
				outFile.createNewFile();
			}
			pngIn = new FileInputStream(pngFile);
			inputIn = new FileInputStream(inputFile);
			out = new FileOutputStream(outFile);
			//获取最后一个数据块，即IEND数据块
			DataBlock iendBlock = png.getDataBlocks().get(png.getDataBlocks().size() - 1);
			//修改IEND数据块数据长度：原来的长度+要隐藏文件的长度
			long iendLength = ByteUtil.highByteToLong(iendBlock.getLength());
			iendLength += inputFile.length();
			iendBlock.setLength(ByteUtil.longToHighByte(iendLength, iendBlock.getLength().length));
			//修改IEND crc信息：保存隐藏文件的大小（字节），方便后面读取png时找到文件内容的位置，并读取
			iendBlock.setCrc(ByteUtil.longToHighByte(inputFile.length(), iendBlock.getCrc().length));
			//写入文件头部信息
			out.write(png.getPngHeader().getFlag());
			//写入数据块信息
			String hexCode = null;
			for(int i = 0; i < png.getDataBlocks().size(); i++) {
				DataBlock dataBlock = png.getDataBlocks().get(i);
				hexCode = ByteUtil.byteToHex(dataBlock.getChunkTypeCode(), 
						0, dataBlock.getChunkTypeCode().length);
				hexCode = hexCode.toUpperCase();
				out.write(dataBlock.getLength());
				out.write(dataBlock.getChunkTypeCode());
				//写数据块数据
				if(BlockUtil.isIEND(hexCode)) {
					//写原来IEND数据块的数据
					if(dataBlock.getData() != null) {
						out.write(dataBlock.getData());
					}
					//如果是IEND数据块，那么将文件内容写入IEND数据块的数据中去
					len = -1;
					while((len = inputIn.read(buf)) > 0) {
						out.write(buf, 0, len);
					}
				} else {
					out.write(dataBlock.getData());
				}
				out.write(dataBlock.getCrc());
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {
				if(pngIn != null) {
					pngIn.close();
				}
				if(inputIn != null) {
					inputIn.close();
				}
				if(out != null) {
					out.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw e;
			}
		}
		
	}
	
	/**
	 * 将指定的文件信息写入到png文件中，并输出到指定的文件中
	 * @param pngFileName			png文件名
	 * @param inputFileName			要隐藏的文件名
	 * @param outFileName			输出文件名
	 * @throws IOException 
	 */
	public static void writeFileToPng(String pngFileName, String inputFileName, String outFileName) throws IOException {
		Png png = readPng(pngFileName);
		wirteFileToPng(png, pngFileName, inputFileName, outFileName);
	}
	
	/**
	 * 读取png文件中存储的信息，并写入到指定指定输出文件中
	 * @param pngFileName		png文件名
	 * @param outFileName		指定输出文件名
	 * @throws IOException 
	 */
	public static void readFileFromPng(String pngFileName, String outFileName) throws IOException {
		File pngFile = new File(pngFileName);
		File outFile = new File(outFileName);
		InputStream pngIn = null;
		OutputStream out = null;
		//记录输入流读取位置
		long pos = 0;
		int len = -1;
		byte[] buf = new byte[1024];
		try {
			if(!outFile.exists()) {
				outFile.createNewFile();
			}
			pngIn = new BufferedInputStream(new FileInputStream(pngFile));
			out = new FileOutputStream(outFile);
			DataBlock dataBlock = new CommonBlock();
			//获取crc的长度信息，因为不能写死，所以额外获取一下
			int crcLength = dataBlock.getCrc().length;
			byte[] fileLengthByte = new byte[crcLength];
			pngIn.mark(0);
			//定位到IEND数据块的crc信息位置，因为写入的时候我们往crc写入的是隐藏文件的大小信息
			pngIn.skip(pngFile.length() - crcLength);
			//读取crc信息
			pngIn.read(fileLengthByte);
			//获取到隐藏文件的大小（字节）
			int fileLength = ByteUtil.highByteToInt(fileLengthByte);
			//重新定位到开始部分　
			pngIn.reset();
			//定位到隐藏文件的第一个字节
			pngIn.skip(pngFile.length() - fileLength - crcLength);
			pos = pngFile.length() - fileLength - crcLength;
			//读取隐藏文件数据
			while((len = pngIn.read(buf)) > 0) {
				if( (pos + len) > (pngFile.length() - crcLength) ) {
					out.write(buf, 0, (int) (pngFile.length() - crcLength - pos));
					break;
				} else {
					out.write(buf, 0, len);
				}
				pos += len;
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {
				if(pngIn != null) {
					pngIn.close();
				}
				if(out != null) {
					out.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw e;
			}
		}
	}
	/**
	 * 将读取到的文件信息写入到指定png的文件中，并指定输出文件
	 * @param pngFileName		png文件名
	 * @param append		要添加的buye[]
	 * @param outFileName		输出文件名，内容包括png数据和要隐藏文件的信息
	 * @throws IOException
	 */
	public static void wirteByteArrayToPng( String pngFileName, byte[] append, String outFileName) throws IOException {
		Png png;
		try {
			 png = readPng(pngFileName);
		} catch (IOException e) {
			Log.e("readPng", "readPng:", e);
			throw e;
		}
		File pngFile = new File(pngFileName);
		//File inputFile = new File(inputFileName);
		File outFile = new File(outFileName);
		InputStream pngIn = null;
		InputStream inputIn = null;
		OutputStream out = null;
		int len = -1;
		byte[] buf = new byte[1024];
		try {
			if(!outFile.exists()) {
				outFile.createNewFile();
			}
			pngIn = new FileInputStream(pngFile);
		//	inputIn=new ByteArrayInputStream(append);
			//inputIn = new FileInputStream(inputFile);
			out = new FileOutputStream(outFile);
			//获取最后一个数据块，即IEND数据块
		//	DataBlock iendBlock = png.getDataBlocks().get(png.getDataBlocks().size() - 1);
			//修改IEND数据块数据长度：原来的长度+要隐藏文件的长度
		//	long iendLength = ByteUtil.highByteToLong(iendBlock.getLength());
		//	iendLength += append.length;
		//	iendBlock.setLength(ByteUtil.longToHighByte(iendLength, iendBlock.getLength().length));
			//修改IEND crc信息：保存隐藏文件的大小（字节），方便后面读取png时找到文件内容的位置，并读取
		//	iendBlock.setCrc(ByteUtil.longToHighByte(append.length, iendBlock.getCrc().length));
			//写入文件头部信息
			out.write(png.getPngHeader().getFlag());
			//写入数据块信息
			String hexCode = null;
			for(int i = 0; i < png.getDataBlocks().size(); i++) {
				DataBlock dataBlock = png.getDataBlocks().get(i);
				hexCode = ByteUtil.byteToHex(dataBlock.getChunkTypeCode(),
						0, dataBlock.getChunkTypeCode().length);
				hexCode = hexCode.toUpperCase();
				out.write(dataBlock.getLength());
				out.write(dataBlock.getChunkTypeCode());
				//写数据块数据
			/*	if (BlockUtil.isIEND(hexCode)) {
					//写原来IEND数据块的数据
					if (dataBlock.getData() != null) {
						out.write(dataBlock.getData());
						out.write(dataBlock.getCrc());
					}
					//如果是IEND数据块，那么将文件内容写入IEND数据块的数据中去
					len = -1;
					while ((len = inputIn.read(buf)) > 0) {
						out.write(buf, 0, len);
					}
				} else {*/
				if (!BlockUtil.isIEND(hexCode)) {
					//写原来IEND数据块的数据
					out.write(dataBlock.getData());
					out.write(dataBlock.getCrc());
				}else {
					out.write(dataBlock.getCrc());
					out.write(append);
				}





				//}
			/*	if (BlockUtil.isIHDR(hexCode)) {
					 HISTBlock dataBlockHIST = new HISTBlock();
					long lenghtOfHIST=384*288*4;
                    byte[] ByteLenghtOfHIST=new byte[4];
					 ByteLenghtOfHIST=ByteUtil.longToHighByte(lenghtOfHIST,4);
					byte [] HIST={68,49,53,54};
					byte []NeedCalCrcData=new byte[append.length+HIST.length];
					System.arraycopy(HIST,0,NeedCalCrcData,0,HIST.length);
					System.arraycopy(append,0,NeedCalCrcData,HIST.length,append.length);
					dataBlockHIST.setLength(ByteLenghtOfHIST);
					dataBlockHIST.setChunkTypeCode(HIST);
					dataBlockHIST.setData(append);
					CRC32 crc32=new CRC32();
					crc32.update(NeedCalCrcData);
					long LongCrc=crc32.getValue();
					byte[] crc=ByteUtil.longToHighByte(LongCrc,4);
					dataBlockHIST.setCrc(crc);
					out.write(NeedCalCrcData);
					out.write(crc);
				}*/
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			try {
				if(pngIn != null) {
					pngIn.close();
				}
				if(inputIn != null) {
					inputIn.close();
				}
				if(out != null) {
					out.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw e;
			}
		}

	}

}
