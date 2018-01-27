package com.ffmpeg.intf;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.ffmpeg.intf.FFmpegWrapper.FrameInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

public class FFmpegPlayer {
	private final String TAG = "FFmpegPlayer";
	private Surface mSurface;
	private FrameInfo fi = new FrameInfo();
	private long m_ldts = -1;
	FFmpegWrapper ffw = new FFmpegWrapper();
	private String mUrl = "";
	private Context mContext = null;
	private boolean mbStopFlag = false;
	private PlayerThread mPT = null;
	private int miFormatCtx = -1;
	private int miPacketHdl = -1;
	private int mProbesize = -1;
	private byte[] mavDataAry = null;
	private int mStreamIdxVideo = -1;
	private int mStreamIdxAudio = -1;
	
	private final int MAX_TRY_TIMES = 3;	 	
	private int miState;
	public static final int ST_Idle = 1;
	public static final int ST_Opening = 1;
	public static final int ST_Playing = 2;
	public static final int ST_RecvEOF = 3;
	public static final int ST_F_Create = -1;
	public static final int ST_F_Open = -2;
	public static final int ST_F_AnalyzeFmt = -3;
	
	private MediaCodec mcAudioDec;
	private MediaCodec mcVideoDec;
	private AudioTrack audioTrack;
	private final long kTimeOutUs4In = 20*1000;
	private final long kTimeOutUs4Out = 1*1000;
	
	private int mSampleRate;					
	private int mChannelCount; 
	
	public int GetState(){
		return miState;
	}
	
	public FFmpegPlayer(Context cContext, Surface surface, String url, int probesize){
		mContext = cContext;
		mSurface = surface;
		mUrl = url;
		mbStopFlag = false;
		mProbesize = probesize; 
		if(mavDataAry == null) mavDataAry = new byte[200*1024]; 
		miState = ST_Idle;		
	}
	
	public int Start(){
		mbStopFlag = false;
		mStreamIdxVideo = -1;
		mStreamIdxAudio = -1;
		mSampleRate = 0;					
		mChannelCount = 0;		
		
		if(mPT == null) mPT = new PlayerThread();
		if(mPT == null) return -1;
		mPT.start();
		return 0;
	}
	
	public int Stop(){
		mbStopFlag = true;
		return 0;
	}
	
	private class PlayerThread extends Thread {
		@Override
		public void run() {
			int i;
			int iRetVal;
			
			ffw.Init();
			miFormatCtx = ffw.CreateFormatCtx();
			miPacketHdl = ffw.CreatePacket();
			if(miFormatCtx == 0 || miPacketHdl == 0){
				miState = ST_F_Create;
				Log.e(TAG, "CreateFormatCtx fail.");
				return; 
			}
					
			iRetVal = ffw.OpenInput(miFormatCtx, mUrl, mProbesize);
			if(iRetVal != 0){
				miState = ST_F_Open;
				Log.d(TAG, "");
			}
			
			for(i=0; i<MAX_TRY_TIMES; i++){
				iRetVal = ffw.FindStreamInfo(miFormatCtx);
				if(iRetVal == 0) break;
			}		
			
			iRetVal = AnalyzeFormat();
			if(iRetVal != 0){
				miState = ST_F_AnalyzeFmt;
				return;
			}
			
			if(mStreamIdxVideo != -1) mcVideoDec.start();
			if(mStreamIdxAudio != -1) mcAudioDec.start();
			
			if(mStreamIdxAudio != -1){
				int channelConfiguration = mChannelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
		        int minSize = AudioTrack.getMinBufferSize( mSampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
		        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, channelConfiguration,
		        		AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
		        audioTrack.play();
			}	        	 
			
			ByteBuffer[] inputBuffersVideo = null;
			ByteBuffer[] outputBuffersVideo = null;
			BufferInfo infoVideo = null;
			ByteBuffer[] inputBuffersAudio = null;
			ByteBuffer[] outputBuffersAudio = null;
			BufferInfo infoAudio = null;
					
			if(mStreamIdxVideo != -1){
				inputBuffersVideo = mcVideoDec.getInputBuffers();
				outputBuffersVideo = mcVideoDec.getOutputBuffers();
				infoVideo = new BufferInfo();
			}			
			
			if(mStreamIdxAudio != -1){
				inputBuffersAudio = mcAudioDec.getInputBuffers();
				outputBuffersAudio = mcAudioDec.getOutputBuffers();
				infoAudio = new BufferInfo();
			}
			
			long startMs = System.currentTimeMillis();
			int iCounter = 0;
			int outIndexVideo = -1;
			byte[] chunk = new byte[20 * 1024];
			while(mbStopFlag == false){
				int sampleSize = -1;
				sampleSize = ffw.ReadFrame(miFormatCtx, miPacketHdl, fi, mavDataAry);
				if(sampleSize < 0){
					//miState = ST_RecvEOF;					
					//return;
					try {
                        Thread.sleep(1000);} catch (InterruptedException e) {}
					continue;
				}
				
				//byte[] bt = new byte[54];
				//System.arraycopy(mavDataAry, 0, bt, 0, 54);
				//Log.d(TAG, fi.stream_index+", iCounter="+(iCounter++)+", "+byte2HexStr(bt));
				
				long dts = fi.pts_h * 0xffFFffFF + fi.pts_l; 
				if(m_ldts == -1) m_ldts = dts;
				dts = dts - m_ldts;			
				
				if(fi.stream_index == mStreamIdxVideo){//i_f_1
					int inIndex = mcVideoDec.dequeueInputBuffer(kTimeOutUs4In);
					if(inIndex >= 0){
						ByteBuffer buffer = inputBuffersVideo[inIndex];
						int i_buf_cap = buffer.capacity();
						if(i_buf_cap < sampleSize){ 
							Log.w(TAG, "--------------------------------video sampleSize="+sampleSize+", i_buf_cap="+i_buf_cap);
						}
						else{
							buffer.clear();	
							buffer.put(mavDataAry, 0, sampleSize);						
							buffer.flip();
							mcVideoDec.queueInputBuffer(inIndex, 0, sampleSize, dts, 0);
						}
					}
					else{
						Log.w(TAG, "--------------------------------video dequeueInputBuffer timeout!");
					}
					
					if(outIndexVideo == -1) outIndexVideo = mcVideoDec.dequeueOutputBuffer(infoVideo, kTimeOutUs4Out);
					switch (outIndexVideo) {
					case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:	outputBuffersVideo = mcVideoDec.getOutputBuffers();			outIndexVideo=-1; break;
					case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:		Log.d(TAG, "New format " + mcVideoDec.getOutputFormat());	outIndexVideo=-1; break;
					case MediaCodec.INFO_TRY_AGAIN_LATER:			/*Log.d(TAG, "video dequeueOutputBuffer timed out!");*/		outIndexVideo=-1; break;
					default:
						ByteBuffer buffer = outputBuffersVideo[outIndexVideo];
						if((infoVideo.presentationTimeUs / 1000) > (System.currentTimeMillis() - startMs)) break;
						mcVideoDec.releaseOutputBuffer(outIndexVideo, true);
						outIndexVideo = -1;
						break;
					}				
				}//end of i_f_1				
				
				if(fi.stream_index == mStreamIdxAudio){//i_f_2
					int inIndex = mcAudioDec.dequeueInputBuffer(kTimeOutUs4In);
					if(inIndex >= 0){
						ByteBuffer buffer = inputBuffersAudio[inIndex];
						int i_buf_cap = buffer.capacity();
						if(i_buf_cap < sampleSize){ 
							Log.w(TAG, "--------------------------------audio sampleSize="+sampleSize+", i_buf_cap="+i_buf_cap);
						}
						else{
							buffer.clear();	
							buffer.put(mavDataAry, 0, sampleSize);						
							buffer.flip();
							mcAudioDec.queueInputBuffer(inIndex, 0, sampleSize, dts, 0);
						}
					}
					else{
						Log.w(TAG, "--------------------------------audio dequeueInputBuffer timeout!");
					}
					
					int res = mcAudioDec.dequeueOutputBuffer(infoAudio, kTimeOutUs4Out);
		            if (res >= 0) {
		                int outputBufIndex = res;
		                ByteBuffer buf = outputBuffersAudio[outputBufIndex];

		                buf.get(chunk, 0, infoAudio.size);
		                buf.clear();
		                if(chunk.length > 0){        	
		                	audioTrack.write(chunk, 0, infoAudio.size);
		                }
		                mcAudioDec.releaseOutputBuffer(outputBufIndex, false);
		                if ((infoAudio.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
		                    Log.d(TAG, "saw output EOS.");
		                }
		            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
		            	outputBuffersAudio = mcAudioDec.getOutputBuffers();
                        Log.d(TAG, "audio output buffers have changed.");
		            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
		                MediaFormat oformat = mcAudioDec.getOutputFormat();
                        Log.d(TAG, "audio output format has changed to " + oformat);
		            } else {
		                Log.d(TAG, "dequeueOutputBuffer returned " + res);
		            }
				}//end of i_f_2	
				
			}//end of while
			
			if(mStreamIdxVideo != -1){
				mcVideoDec.stop();
				mcVideoDec.release();
			}
			
			if(mStreamIdxAudio != -1){
				mcAudioDec.stop();
				mcAudioDec.release();
				audioTrack.stop();
				audioTrack.release();
			}
		}
								
	}//end of PlayerThread
	
	private int AnalyzeFormat(){
		mStreamIdxVideo = -1;
		mStreamIdxAudio = -1;
		
		int iTotalStream = ffw.GetTotalStreamNumber(miFormatCtx);
		Log.d(TAG, "iTotalStream="+iTotalStream);
		for(int i=0; i<iTotalStream; i++){
			int iCodecType = ffw.GetStreamCodecType(miFormatCtx, i);
			if(iCodecType == FFmpegDef.AVMEDIA_TYPE_VIDEO){
				mStreamIdxVideo = i;
				CreateVideoDecoder();
			}
			if(iCodecType == FFmpegDef.AVMEDIA_TYPE_AUDIO){
				mStreamIdxAudio = i;
				CreateAudioDecoder();				
			}
		}
		if(mStreamIdxVideo == -1 && mStreamIdxAudio == -1) return -1;
		return 0;
	}		
	
	private int CreateVideoDecoder(){
		int iIdx = mStreamIdxVideo;
		int iCodecId = ffw.GetStreamCodecId(miFormatCtx, iIdx);
		String mime = "video/avc";
		if(iCodecId == FFmpegDef.AV_CODEC_ID_H264) mime = "video/avc";
		Log.d(TAG, "iCodecId="+iCodecId);
		
		int width = ffw.GetStreamWidth(miFormatCtx, iIdx);					
		int height = ffw.GetStreamHeight(miFormatCtx, iIdx); 
		Log.d(TAG, "width="+width);
		Log.d(TAG, "height="+height);
		
		float ffps = ffw.GetStreamFPS(miFormatCtx, iIdx);
		Log.d(TAG, "ifps="+ffps);
		
		MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
		
		int iExtraLen = ffw.GetStreamExtraDataLen(miFormatCtx, iIdx); 
		if(iExtraLen > 0){
			//ByteBuffer extBuf = ByteBuffer.allocate(iExtraLen);
			byte[] extBuf = new byte[iExtraLen];
			int iGetLen = ffw.GetStreamExtraData(miFormatCtx, iIdx, extBuf);
			//for(int j=0; j<iExtraLen; j++){
			//	Log.d(TAG, Byte.toString(extBuf[j]));
			//}
			Log.d(TAG, "ExtraData="+byte2HexStr(extBuf));
			Log.d(TAG, "iGetLen="+iGetLen);
			
			if((extBuf[0] != 0 || extBuf[1] != 0 || extBuf[2] != 0 || extBuf[3] != 1)){
				int i_csd0_len = extBuf[7];
				byte[] b_csd0 = new byte[i_csd0_len+4];
				b_csd0[0] = 0; b_csd0[1] = 0; b_csd0[2] = 0; b_csd0[3] = 1;
				System.arraycopy(extBuf, 7+1, b_csd0, 4, i_csd0_len);
				ByteBuffer extByteBuf_cds0 = ByteBuffer.wrap(b_csd0);
				format.setByteBuffer("csd-0", extByteBuf_cds0);
				
				int i_csd1_len = extBuf[7 + i_csd0_len + 3];
				byte[] b_csd1 = new byte[i_csd1_len+4]; 
				b_csd1[0] = 0; b_csd1[1] = 0; b_csd1[2] = 0; b_csd1[3] = 1;
				System.arraycopy(extBuf, 7+i_csd0_len+3+1, b_csd1, 4, i_csd1_len);
				ByteBuffer extByteBuf_cds1 = ByteBuffer.wrap(b_csd1);
				format.setByteBuffer("csd-1", extByteBuf_cds1);							
			}
			else{							
				ByteBuffer extByteBuf = ByteBuffer.wrap(extBuf);
				format.setByteBuffer("csd-0", extByteBuf);
			}
			Log.d(TAG, "iExtraLen="+iExtraLen);

			try {
				mcVideoDec = MediaCodec.createDecoderByType(mime);
			} catch (IOException e) {
				e.printStackTrace();
			}
			mcVideoDec.configure(format, mSurface, null, 0);	 
			Log.d(TAG, "video_stream_index="+mStreamIdxVideo);
		}			
		return 0;
	}
	
	private int CreateAudioDecoder(){
		int iIdx = mStreamIdxAudio;
		
		int iCodecId = ffw.GetStreamCodecId(miFormatCtx, iIdx);
		String mime = "audio/mp4a-latm";
		if(iCodecId == FFmpegDef.AV_CODEC_ID_AAC) mime = "audio/mp4a-latm";
		Log.d(TAG, "iCodecId="+iCodecId);
		
		mSampleRate = ffw.GetSampleRate(miFormatCtx, iIdx);					
		mChannelCount = ffw.GetChannels(miFormatCtx, iIdx);
		//if(mChannelCount > 2) mChannelCount = 2; //hacker 
		Log.d(TAG, "mSampleRate="+mSampleRate);
		Log.d(TAG, "mChannelCount="+mChannelCount);
							
		MediaFormat format = MediaFormat.createAudioFormat(mime, mSampleRate, mChannelCount);
		
		int iExtraLen = ffw.GetStreamExtraDataLen(miFormatCtx, iIdx); 
		if(iExtraLen > 0){
			//ByteBuffer extBuf = ByteBuffer.allocate(iExtraLen);
			byte[] extBuf = new byte[iExtraLen];
			int iGetLen = ffw.GetStreamExtraData(miFormatCtx, iIdx, extBuf);
			//for(int j=0; j<iExtraLen; j++){
			//	Log.d(TAG, Byte.toString(extBuf[j]));
			//}
			Log.d(TAG, "ExtraData="+byte2HexStr(extBuf));
			Log.d(TAG, "iGetLen="+iGetLen);
			
			ByteBuffer extByteBuf = ByteBuffer.wrap(extBuf);
			format.setByteBuffer("csd-0", extByteBuf);
		}
		Log.d(TAG, "iExtraLen="+iExtraLen);

		try {
			mcAudioDec = MediaCodec.createDecoderByType(mime);
		} catch (IOException e) {
			e.printStackTrace();
		}
		mcAudioDec.configure(format, null, null, 0);
		Log.d(TAG, "audio_stream_index="+mStreamIdxAudio);
		return 0;
	}	
	
	public static String byte2HexStr(byte[] b)
	{    
	    String stmp="";
	    StringBuilder sb = new StringBuilder("");
	    for (int n=0;n<b.length;n++)    
	    {    
	        stmp = Integer.toHexString(b[n] & 0xFF);
	        sb.append((stmp.length()==1)? "0"+stmp : stmp);    
	        sb.append(" ");    
	    }    
	    return sb.toString().toUpperCase().trim();    
	}	
}

	