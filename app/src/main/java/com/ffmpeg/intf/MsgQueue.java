package com.ffmpeg.intf;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MsgQueue {	
	private String TAG;
	private int miAryLen = 0;
	public byte[] mavDataAry = null;
	private int miTail;
	private int miHead;
	private BlockingQueue<clsIndex> mQVOcp = new LinkedBlockingQueue<clsIndex>();
	
	public class clsIndex{
		int iStart;
		int iEnd;
		long ldts;
	}
	
	public MsgQueue(int iAryLen, String strTag){
		TAG = "MsgQueue/" + strTag;
		miTail = 0;
		miHead = 0;		
		miAryLen = iAryLen;
		mavDataAry = new byte[miAryLen];
		mQVOcp.clear();
	}
	
	public int Reset(){		
		miTail = 0;
		miHead = 0;		
		mQVOcp.clear();
		return 0;
	}
	
	public int Close(){
//		mQVOcp.clear();
		mavDataAry = null;
		return 0;
	}	
	
	public int Put(byte[] avData, int iDataLen, long ldts){
		int iStart;
		int iEnd;
		if(miHead >= miTail){
			if(miHead + iDataLen > miAryLen){
				if(iDataLen >= miTail) return -1;
				System.arraycopy(avData, 0, mavDataAry, 0, iDataLen);
				iStart = 0;
				iEnd = iStart + iDataLen;
				miHead = iEnd;			
			}
			else{
				System.arraycopy(avData, 0, mavDataAry, miHead, iDataLen);
				iStart = miHead;
				iEnd = iStart + iDataLen;
				miHead = iEnd;
			}
		}
		else{
			if(miHead + iDataLen >= miTail) return -1;
			System.arraycopy(avData, 0, mavDataAry, miHead, iDataLen);
			iStart = miHead;
			iEnd = iStart + iDataLen;
			miHead = iEnd;			
		}
		clsIndex ci = new clsIndex();
		ci.iStart = iStart;
		ci.iEnd = iEnd;
		ci.ldts = ldts;
		//Log.d(TAG, "Put >>> iDataLen="+iDataLen+", iStart="+iStart+", miHead="+miHead+", ldts="+ldts/1000);
		mQVOcp.offer(ci);
		return 0;
	}
	
	public int PutEx1(byte[] avData, int iOffset, int iDataLen, long ldts){
		int iStart;
		int iEnd;
		if(miHead >= miTail){
			if(miHead + iDataLen > miAryLen){
				if(iDataLen >= miTail) return -1;
				System.arraycopy(avData, iOffset, mavDataAry, 0, iDataLen);
				iStart = 0;
				iEnd = iStart + iDataLen;
				miHead = iEnd;			
			}
			else{
				System.arraycopy(avData, iOffset, mavDataAry, miHead, iDataLen);
				iStart = miHead;
				iEnd = iStart + iDataLen;
				miHead = iEnd;
			}
		}
		else{
			if(miHead + iDataLen >= miTail) return -1;
			System.arraycopy(avData, iOffset, mavDataAry, miHead, iDataLen);
			iStart = miHead;
			iEnd = iStart + iDataLen;
			miHead = iEnd;			
		}
		clsIndex ci = new clsIndex();
		ci.iStart = iStart;
		ci.iEnd = iEnd;
		ci.ldts = ldts;
		//Log.d(TAG, "Put >>> iDataLen="+iDataLen+", iStart="+iStart+", miHead="+miHead+", ldts="+ldts/1000);
		mQVOcp.offer(ci);
		return 0;
	}
	
	public int Get(byte[] avData, int iBufLen, long[] dts, int iTimeOut){		
		clsIndex ci = null;
		try{ci = mQVOcp.poll(iTimeOut, TimeUnit.MILLISECONDS);}
		catch(Exception e){
			e.printStackTrace();
			ci = null;
		}	
		if(ci == null) return -1;
		
		int iDataLen = ci.iEnd - ci.iStart;
		if(iDataLen > iBufLen || mavDataAry == null){
			Log.d(TAG, "iDataLen("+iDataLen+")>("+iBufLen+")");
			return -1;
		}
		System.arraycopy(mavDataAry, ci.iStart, avData, 0, iDataLen);
		miTail = ci.iEnd;
		dts[0] = ci.ldts;
		//Log.d(TAG, "Get >>> iDataLen="+iDataLen+", iStart="+ci.iStart+", miTail="+miTail+", ldts="+ci.ldts/1000);
		return iDataLen;
	}
	
	public int GetMsgNum(){
		return mQVOcp.size();
	}
	
	public int DelMsg(int iNumMsg){
		int i=0; 
		clsIndex ci = null;
		for(i=0; i<iNumMsg; i++){
			try{ci = mQVOcp.poll(0, TimeUnit.MILLISECONDS);}
			catch(Exception e){
				e.printStackTrace();
				ci = null;
			}	
			if(ci == null) return -1;
		}
		return 0;
	}
}
