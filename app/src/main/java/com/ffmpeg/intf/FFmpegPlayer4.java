package com.ffmpeg.intf;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.ffmpeg.intf.FFmpegWrapper.FrameInfoEx;

import java.nio.ByteBuffer;

public class FFmpegPlayer4 {
	private final String TAG = "FFmpegPlayer4";
	private String mObjTAG = "";
	private Surface mSurface;
	private FrameInfoEx fiEx = new FrameInfoEx();
	private long m_ldts = -1;
	FFmpegWrapper ffw = new FFmpegWrapper();
	private String mUrl = "";
	private Context mContext = null;
	private boolean mbStopFlag = false;
	private PlayerThread mPT = null;
	private int miFormatCtx = -1;
	private int miAudioDecCtx = 0;
	private int miPacketHdl = -1;
	private int mProbesize = -1;
	private byte[] mavDataAry = null;
	public int mStreamIdxVideo = -1;
	public int mStreamIdxAudio = -1;
	AudioTimestamp ts = null;
	
	private final int MAX_TRY_TIMES = 1;
	private int miState;
	public static final int ST_Idle = 1;
	public static final int ST_Opening = 2;
	public static final int ST_Playing = 3;
	public static final int ST_RecvEOF = 4;
	public static final int ST_GotFormat = 5;
	public static final int ST_F_Create = -1;
	public static final int ST_F_Open = -2;
	public static final int ST_F_AnalyzeFmt = -3;	//解码失败
	public static final int ST_F_StartVideo = -4;
	public static final int ST_NetFail = -5;
	public static final int ST_F_EndOfPlay = -6;
	
	private boolean mbDecAudByMC = true;
	private boolean mbIsAAC = false; 
	private boolean mbSeekOPD = false;
	private boolean mbSeekOP = false;
	private boolean mbDecodeThreadOver;
	private long mlSeekTime = 0;
	private long mlCurrentPosition = 0;
	private MediaCodec mcVideoDec;
	private MediaCodec mcAudioDec;
	private AudioTrack audioTrack;
	private final int MAX_PACKET_LEN = 200*1024;		//原始：200*1024
	
	MsgQueue mqVideo = null;
	MsgQueue mqAudio = null;
	
	MediaFormat formatVideo = null;
	MediaFormat formatAudio = null;
	
	private int mSampleRate;					
	private int mChannelCount; 
	private boolean mbLiveStreaming = false; 
	private boolean mbEnableTimeout = true;
	
	private int miSkipFrameCounter = 0;
	private long mlAudioLost = 0;
	private long mlVideoLost = 0;
	private long miCounter = 0;
	private boolean mbGotIFrame = false;
	private long ml_delay = 0;
	private boolean mInteraction = false;
	private int miReceiveFailCounter = 0;
	private long mlVideoFrameCntr = 0;
	private long mlBufVideoFrame = 0;
	private boolean mAudio; // 是否需要音频
	public int SetBufferFrame(int iBufVideoFrame){
		mlBufVideoFrame = iBufVideoFrame;
		return 0;
	}
	
	public int GetState(){
		return miState;
	}
	public void setAudio(boolean audio){
		this.mAudio = audio;
	}
	public int SetDataSource(String url, int probesize, boolean interaction) {
		mUrl = url;
		mProbesize = probesize;
		mInteraction = interaction;
		return 0;
	}

	public FFmpegPlayer4(Context cContext, Surface surface, int iVBufLen, int iABufLen, String strObjTAG) {
		mObjTAG = strObjTAG;
		mqVideo = new MsgQueue(iVBufLen, "V");
		mqAudio = new MsgQueue(iABufLen, "A");
		
		mContext = cContext;
		mSurface = surface;
		
		mavDataAry = new byte[MAX_PACKET_LEN];
		mbStopFlag = false;		
		miState = ST_Idle;			
	}
	
	public int Start(boolean bLiveStreaming, boolean bEnableTimeout){
		if (miState > ST_Idle) {
			Log.d(TAG+ mObjTAG, "can not play! miState=" + miState);
			return -1;
		}
		miState = ST_Idle;
		mbStopFlag = false;
		mStreamIdxVideo = -1;
		mStreamIdxAudio = -1;
		mSampleRate = 0;					
		mChannelCount = 0;	
		miState = ST_Opening;
		mbLiveStreaming = bLiveStreaming;
		mbEnableTimeout = bEnableTimeout;
		
		mbIsAAC = false; 
		mbSeekOPD = false;
		mbSeekOP = false;
		mlSeekTime = 0;
		mlCurrentPosition = 0;
		
		int i;
		if(mqVideo!=null) mqVideo.Reset();
		if(mqAudio!=null) mqAudio.Reset();
		
//		Log.d(TAG, "mQVIdle.size()="+mQVIdle.size());
//		for(i=0; i<MAX_VIDEO_NUM; i++){			
//			Object obj = mQVIdle.poll();
//			Log.d(TAG, "obj="+obj);
//			int iIdx = (Integer) obj;
//			Log.d(TAG, "iIdx="+iIdx);
//		}
		
		mPT = new PlayerThread();
		if(mPT == null) return -1;
		mPT.start();
		return 0;
	}
	
	public int Stop(){
		mbStopFlag = true;
		mPT = null;
		return 0;
	}
	
	public int Release(){
		if(mqVideo != null) mqVideo.Close();
		if(mqAudio != null) mqAudio.Close();
		mavDataAry = null;
		return 0;
	}
		
	public int SeekFile(long lSeekTime){
		mlSeekTime = lSeekTime + m_ldts;
		mbSeekOP = true;				
		return 0;
	}
	
	public long GetDuration(){
		return ffw.GetDuration(miFormatCtx);
	}

	public long GetCurrentPosition(){
		return mlCurrentPosition;
	}
	
	private int GetDataFromFFmpeg(){		
		if(mbSeekOP){
			Log.d(TAG+ mObjTAG, "mlSeekTime="+mlSeekTime);
			ffw.Flush(miFormatCtx);
			ffw.SeekFile(miFormatCtx, -1, 0, mlSeekTime, mlSeekTime, 0);			
			mqVideo.Reset();
			mqAudio.Reset();
			m_ldts = -1;		
			mbGotIFrame = false;
			
			mbSeekOP = false;
			mbSeekOPD = true;				
		}
		
		if(mbSeekOPD){
			if(mbDecodeThreadOver){
				mlVideoFrameCntr = 0;
				new DecodeThread().start();
				mbSeekOPD = false;
			}
		}
		
		int sampleSize = -1;
		try{
			sampleSize = ffw.ReadFrame3(miFormatCtx, mbDecAudByMC ? 0 : miAudioDecCtx, miPacketHdl, mbEnableTimeout?1:0, fiEx, mavDataAry);
		}catch (Exception e)
		{
			e.printStackTrace();
		}
		if(sampleSize < 0){
//			Log.d(TAG+ mObjTAG, "sampleSize=" + sampleSize);
			// miState = ST_RecvEOF;
			// return;
			// ffw.Flush(miFormatCtx);
			if (sampleSize == -1){
				miReceiveFailCounter++;
			}else if(sampleSize == -500){
				m_ldts = -1;
			}
			if ((miReceiveFailCounter % 150) == 0) {
				Log.d(TAG+mObjTAG, "sampleSize=" + sampleSize);
				miState = ST_NetFail;
				return -1;
			}
			try {
                Thread.sleep(5);} catch (InterruptedException e) {}
			return -1;
		}
		miState = ST_Playing;
		long dts_l = fiEx.dts_l;
		if(fiEx.dts_l < 0) dts_l = 0x100000000L + fiEx.dts_l; 					
		long dts_h = fiEx.dts_h;
		if(fiEx.dts_h < 0) dts_h = 0x100000000L + fiEx.dts_h;						
		long dts = 0x100000000L * dts_h + dts_l;
		
		if(fiEx.stream_index == mStreamIdxVideo && fiEx.flag == 1) mbGotIFrame = true;
		if(mStreamIdxVideo == -1) mbGotIFrame = true;
		if(mbGotIFrame==false) return -1;				
		
		//byte[] bt = new byte[54];
		//System.arraycopy(mavDataAry, 0, bt, 0, 54);
		//if(fiEx.stream_index == mStreamIdxAudio) Log.d(TAG, fiEx.stream_index+", iCounter="+(iCounter++)+", "+byte2HexStr(bt));		
		
		if(dts < 0 || fiEx.dts_h < 0) Log.d(TAG+ mObjTAG, "fiEx.dts_h="+ Integer.toHexString(fiEx.dts_h)+", fiEx.dts_l="+ Integer.toHexString(fiEx.dts_l)+", dts="+ Long.toHexString(dts)+", dts="+dts);
		if(m_ldts == -1){
			if(dts < 0 || fiEx.dts_h < 0){
				//Log.d(TAG, "fiEx.dts_h="+Integer.toHexString(fiEx.dts_h)+", fiEx.dts_l="+Integer.toHexString(fiEx.dts_l)+", dts="+Long.toHexString(dts)+", dts="+dts);
				dts = 0;
			}
			else 
				m_ldts = dts;					
		}
		if(dts<0){
			dts = 0;
		}
		else{
			if(dts != 0) dts = dts - m_ldts + ml_delay;
		}
		miCounter++;
		if(miCounter % 100 == 0)
			if(true || fiEx.stream_index == mStreamIdxAudio) Log.d(TAG+ mObjTAG, fiEx.stream_index+", " +
					"dts="+dts/1000+",m_ldts="+m_ldts/1000+", fiEx.dts_h="+fiEx.dts_h+", fiEx.dts_l="+fiEx.dts_l);
						
		if(fiEx.stream_index == mStreamIdxVideo){
			while(!Thread.interrupted() && mbStopFlag==false){
				if(mqVideo.Put(mavDataAry, sampleSize, dts) < 0){
					if(mbLiveStreaming){
						mlVideoLost++;
						if(mlVideoLost % 100 == 0) Log.d(TAG+ mObjTAG, "m v-full dts="+dts+", lVideoLost="+mlVideoLost);
						break; //满就直接丢掉
					}
					else{
						try {
                            Thread.sleep(10);} catch (InterruptedException e) {}
						continue;								
					}
				}	
				mlVideoFrameCntr++;
				break;
			}
		}								
		else if(fiEx.stream_index == mStreamIdxAudio){
			while(!Thread.interrupted() && mbStopFlag==false){
				int iADTSHeaderLen = 0;
				if(mbIsAAC){
					//ADTS header, Syncword	12bit	all bits must be 1
					byte Syncword0 = mavDataAry[0];
					if(Syncword0 == -1){
						byte Syncword1 = mavDataAry[1];
						Syncword1 = (byte) (Syncword1 & 0xf0);
						if(Syncword1 == -16) iADTSHeaderLen = 7;
					}
				}
				if(mqAudio.PutEx1(mavDataAry, iADTSHeaderLen, sampleSize-iADTSHeaderLen, dts) < 0) //需要去掉7个字节ADTS headers，例如FF F1 50 80 15 DF FC 21
				//if(mqAudio.Put(mavDataAry, sampleSize, dts) < 0)
				{
					if(mbLiveStreaming){
						mlAudioLost++;
						if(mlAudioLost % 100 == 0) Log.d(TAG, "m a-full dts="+dts+", lAudioLost="+mlAudioLost);
						break; //满就直接丢掉
					}
					else{
						try {
                            Thread.sleep(10);} catch (InterruptedException e) {}
						continue;								
					}
				}			
				break;
			}
		}					
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
			miAudioDecCtx = ffw.CreateAudioDecCtx();
			if(miFormatCtx == 0 || miPacketHdl == 0 || miAudioDecCtx == 0){
				miState = ST_F_Create;
				Log.e(TAG + mObjTAG, "CreateFormatCtx fail.");
				return; 
			}
					
			iRetVal = ffw.OpenInput(miFormatCtx, mUrl, mProbesize);
			Log.d(TAG + mObjTAG, "OpenInput >>> iRetVal=" + iRetVal);
			if(iRetVal != 0){
				miState = ST_F_Open;
				Log.d(TAG + mObjTAG, "OpenInput fail. iRetVal=" + iRetVal);
				return;
			}
			
			for(i=0; i<MAX_TRY_TIMES; i++){
				Log.d(TAG + mObjTAG, "FindStreamInfo >>> i=" + i);
				iRetVal = ffw.FindStreamInfo(miFormatCtx);
				if (iRetVal == 0 || mProbesize == -1 || mbStopFlag) break;
			}	
			Log.d(TAG + mObjTAG, "FindStreamInfo >>> iRetVal=" + iRetVal);
			if (iRetVal != 0 || mbStopFlag) {
				ffw.CloseInput(miFormatCtx);
				Log.d(TAG, "FindStreamInfo fail. iRetVal="+iRetVal);
				return;
			}
			
			Log.d(TAG + mObjTAG, "CreateBSFilter...");
			ffw.CreateBSFilter(miFormatCtx);
			
			Log.d(TAG + mObjTAG, "AnalyzeFormat...");
			iRetVal = AnalyzeFormat();
			if (iRetVal != 0 || mbStopFlag) {
				ffw.CloseInput(miFormatCtx);
				miState = ST_F_AnalyzeFmt;
				return;
			}
			miState = ST_GotFormat;
						
			new DecodeThread().start();
								
			if (mbLiveStreaming && !mInteraction) ffw.Flush(miFormatCtx);
			miSkipFrameCounter = 0;
			mlAudioLost = 0;
			mlVideoLost = 0;
			miCounter = 0;
			mbGotIFrame = false;
			ml_delay = 0;
			if (!mbLiveStreaming) mbGotIFrame = true;
			else ml_delay = 0;
			Log.d(TAG+ mObjTAG, "mbLiveStreaming=" + mbLiveStreaming);
			while(!mbStopFlag){
				iRetVal = GetDataFromFFmpeg();
				// if(iRetVal == -1) break;
			}//end of while
			ffw.CloseInput(miFormatCtx); 
			miState = ST_F_EndOfPlay;
			Log.d(TAG+mObjTAG, "PlayerThread over");
		}
								
	}//end of PlayerThread
	
	private class DecodeThread extends Thread {
		public void run() {
			mbDecodeThreadOver = false;
			try {
				DecodeBody();
			} catch (Exception e) {
				e.printStackTrace();
			}
			mbDecodeThreadOver = true;
		}
		
		public void DecodeBody() throws Exception {
			if(mStreamIdxVideo != -1){
				try {
					mcVideoDec = MediaCodec.createDecoderByType(formatVideo.getString(MediaFormat.KEY_MIME));
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				mcVideoDec.configure(formatVideo, mSurface, null, 0);	 
				Log.d(TAG+ mObjTAG, "video_stream_index="+mStreamIdxVideo);
				mcVideoDec.start();
			}
			
			if(mbDecAudByMC==true && mStreamIdxAudio != -1){
				try {
					mcAudioDec = MediaCodec.createDecoderByType(formatAudio.getString(formatAudio.KEY_MIME));
					//mcAudioDec = MediaCodec.createByCodecName("OMX.google.aac.decoder");
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}			
				mcAudioDec.configure(formatAudio, null, null, 0);
				Log.d(TAG+ mObjTAG, "audio_stream_index="+mStreamIdxAudio);
				mcAudioDec.start();
			}
			
			if (mStreamIdxAudio != -1) {
				int channelConfiguration = mChannelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
		        int minSize = AudioTrack.getMinBufferSize( mSampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
				audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
		        audioTrack.play();
			}
			
			ByteBuffer[] inputBuffersV = null;
			ByteBuffer[] outputBuffersV = null;
			if(mStreamIdxVideo != -1){
				inputBuffersV = mcVideoDec.getInputBuffers();
				outputBuffersV = mcVideoDec.getOutputBuffers();				
			}
			BufferInfo infoV = new BufferInfo();
			
			ByteBuffer[] inputBuffersA = null;
			ByteBuffer[] outputBuffersA = null;
			if(mbDecAudByMC==true && mStreamIdxAudio!=-1){
				inputBuffersA = mcAudioDec.getInputBuffers();
				outputBuffersA = mcAudioDec.getOutputBuffers();				
			}
			BufferInfo infoA = new BufferInfo();
			byte[] chunk = new byte[MAX_PACKET_LEN * 4];
			final long kTimeOutUs = 1000;
			
			long startMs = System.currentTimeMillis();

			int iCounter = 0;
			byte[] avDataAry = new byte[MAX_PACKET_LEN];
			long[] dtsAry = new long[1];
			int sampleSize = -1;
			long dts = 0;			
			int inIndexV = -1;
			int inIndexA = -1;
			int outIndexV = -1;
			int outIndexA = -1;
			int iCounterA = 0;
			//long presentationTimeUs = 0;
			int iDropAudioCntr = 0;
			
			while (!Thread.interrupted() && !mbStopFlag && !mbSeekOPD) {
				if(mStreamIdxVideo != -1 && mlVideoFrameCntr < mlBufVideoFrame){
					Log.d(TAG, "mqAudio.GetMsgNum="+mqAudio.GetMsgNum()+", mqVideo.GetMsgNum="+mqVideo.GetMsgNum()+", mlVideoFrameCntr="+mlVideoFrameCntr);
					try {
                        Thread.sleep(10);} catch (InterruptedException e) {}
					startMs = System.currentTimeMillis();
					continue;
				}
				
				//Log.d(TAG, "mqAudio.GetMsgNum="+mqAudio.GetMsgNum()+", mqVideo.GetMsgNum="+mqVideo.GetMsgNum());
            	//if(ts == null) ts = new AudioTimestamp();
            	//if(ts != null){
            	//	boolean bRet = audioTrack.getTimestamp(ts);
            	//	if(bRet){
            	//		presentationTimeUs = ts.nanoTime;
            	//		Log.d(TAG, "bRet="+bRet+", presentationTimeUs="+presentationTimeUs);
            	//	}
            	//}
				//long lPHP = audioTrack.getPlaybackHeadPosition();
				//presentationTimeUs = (lPHP * 1000 / mSampleRate) * 1000;
				//Log.d(TAG, "presentationTimeUs="+presentationTimeUs+", lPHP="+lPHP+", iCounterA="+iCounterA);
            	
				//{{Video
				try{
					if(mStreamIdxVideo != -1){
						if(inIndexV == -1) inIndexV = mcVideoDec.dequeueInputBuffer(kTimeOutUs);
						if (inIndexV >= 0) {
							sampleSize = mqVideo.Get(avDataAry, MAX_PACKET_LEN, dtsAry, 10);
							if(sampleSize != -1){
								dts = dtsAry[0];
								iCounter++;

								ByteBuffer buffer = inputBuffersV[inIndexV];

								int i_buf_cap = buffer.capacity();
								if(i_buf_cap < sampleSize){
									Log.d(TAG+ mObjTAG, "--------------------------------sampleSize="+sampleSize+", i_buf_cap="+i_buf_cap);
								}
								buffer.clear();
								buffer.put(avDataAry, 0, avDataAry.length - 1);
								buffer.flip();

								//byte[] bt = new byte[54];
								//System.arraycopy(avDataAry, 0, bt, 0, 54);
								//Log.d(TAG, "dts="+dts+", iCounter="+iCounter+", "+byte2HexStr(bt));

								mcVideoDec.queueInputBuffer(inIndexV, 0, sampleSize, dts, 0);
								inIndexV = -1;
							}
						}

						if(outIndexV == -1) outIndexV = mcVideoDec.dequeueOutputBuffer(infoV, 1000);
						switch (outIndexV) {
							case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
								Log.d(TAG+ mObjTAG, "INFO_OUTPUT_BUFFERS_CHANGED");
								outputBuffersV = mcVideoDec.getOutputBuffers();
								outIndexV = -1;
								break;
							case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
								Log.d(TAG+ mObjTAG, "New format " + mcVideoDec.getOutputFormat());
								outIndexV = -1;
								break;
							case MediaCodec.INFO_TRY_AGAIN_LATER:
								//Log.d(TAG, "dequeueOutputBuffer timed out! iCounter="+iCounter);
								outIndexV = -1;
								break;
							default:
								// We use a very simple clock to keep the video FPS, or the video
								// playback will be too fast
								//Log.d(TAG, "outIndexV="+outIndexV);
								mlCurrentPosition = infoV.presentationTimeUs;
								if(outIndexV >= 0){
									if ((infoV.presentationTimeUs / 1000) < (System.currentTimeMillis() - startMs))
									//if (infoV.presentationTimeUs < presentationTimeUs)
									{
										outputBuffersV = mcVideoDec.getOutputBuffers();
										mcVideoDec.releaseOutputBuffer(outIndexV, true);
										outIndexV = -1;
									}
								}
								break;
						}

						// All decoded frames have been rendered, we can stop playing now
						if ((infoV.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
							Log.d(TAG+ mObjTAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
							break;
						}
					}
				}catch (Exception e)
				{
					e.printStackTrace();
				}

				//}}
				
				if(mqVideo.GetMsgNum() - mqAudio.GetMsgNum() > 5) continue;
				
				//{{Audio
				if(mStreamIdxAudio == -1) continue;
				
				if(mbDecAudByMC==false){
					sampleSize = mqAudio.Get(avDataAry, MAX_PACKET_LEN, dtsAry, 10);	
					if(sampleSize != -1){
						dts = dtsAry[0];																
						CalcAudioVolume(avDataAry, sampleSize);
	                	audioTrack.write(avDataAry, 0, sampleSize);	                             	
					}					
				}
				else{					
					if(inIndexA == -1) inIndexA = mcAudioDec.dequeueInputBuffer(1000);					
					if (inIndexA >= 0) {
						sampleSize = mqAudio.Get(avDataAry, MAX_PACKET_LEN, dtsAry, 10);
						if(sampleSize != -1){
							dts = dtsAry[0];
							iCounter++;
							iCounterA++;
												
							ByteBuffer buffer = inputBuffersA[inIndexA];
							int i_buf_cap = buffer.capacity();
							if(i_buf_cap < sampleSize){ 
								Log.d(TAG+ mObjTAG, "--------------------------------sampleSize="+sampleSize+", i_buf_cap="+i_buf_cap);
							}
							buffer.clear();	
							buffer.put(avDataAry, 0, sampleSize);						
							buffer.flip();
							
							//byte[] bt = new byte[54];
							//System.arraycopy(avDataAry, 0, bt, 0, 54);
							//Log.d(TAG, "iCounter="+iCounter+", "+byte2HexStr(bt));
							
							mcAudioDec.queueInputBuffer(inIndexA, 0, sampleSize, dts, 0);  
							inIndexA = -1;												
						}
					}
	
					if(outIndexA < 0)  try{outIndexA = mcAudioDec.dequeueOutputBuffer(infoA, kTimeOutUs);}catch(Exception e){}
		            if (outIndexA >= 0) {	            	
		            	long nowUs = (System.currentTimeMillis() - startMs) * 1000;
		            	long lateByUs = nowUs - infoA.presentationTimeUs;
		            	if (lateByUs <= -10000l){
		            		Log.d(TAG, "Audio early by "+(-lateByUs)+" us.");
		            	}
		            	else{	
		            		int outputBufIndex = outIndexA;
		            		long lGate = 30000l;
		            		if(mbLiveStreaming) lGate = 500*1000l; 
//		            		if(mbLiveStreaming && lateByUs > lGate){
//		            			Log.d(TAG, "Audio late by "+(lateByUs)+" us");
//		            		}
		            		if (lateByUs > lGate)		            		
		            		{
		            			iDropAudioCntr++;
		            			Log.d(TAG, "Audio late by "+(lateByUs)+" us, dropping. iDropAudioCntr="+iDropAudioCntr);
		            			//if(iDropAudioCntr > 10){
		            			//	iDropAudioCntr = 0;
		            			//	audioTrack.stop();
		            			//	audioTrack.flush();
		            			//	audioTrack.play();
		            			//}
		            		}
		            		else{				 
		            			iDropAudioCntr = 0;
				                ByteBuffer buf = outputBuffersA[outputBufIndex];
				                
				                buf.get(chunk, 0, infoA.size);
				                buf.clear();
				                if(chunk.length > 0){    
									CalcAudioVolume(chunk, infoA.size);
				                	audioTrack.write(chunk, 0, infoA.size);
				                }				                
		            		}
			                
			                mcAudioDec.releaseOutputBuffer(outputBufIndex, false);
			                if ((infoA.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
							Log.d(TAG+ mObjTAG, "saw output EOS.");
			                }
			                outIndexA = -1;
		            	}
		            } else if (outIndexA == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
		            	outputBuffersA = mcAudioDec.getOutputBuffers();
		                Log.d(TAG, "output buffers have changed.");
		            } else if (outIndexA == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
		                MediaFormat oformat = mcAudioDec.getOutputFormat();
						Log.d(TAG+ mObjTAG, "output format has changed to " + oformat);
		            } else {
		                //Log.d(TAG, "dequeueOutputBuffer returned " + res);
		            }
	
					// All decoded frames have been rendered, we can stop playing now
					if ((infoA.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						Log.d(TAG+ mObjTAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
						break;
					}		
				}
				//}}
			}

			if(mcVideoDec!=null){
				mcVideoDec.stop();
				mcVideoDec.release();
			}			
			
			if(mcAudioDec!=null){
				mcAudioDec.stop();
				mcAudioDec.release();
			}
			
			if(audioTrack!=null)
			{
				audioTrack.stop();
				audioTrack.release();
			}
			Log.d(TAG+mObjTAG, "DecodeThread over");
		}
	}
	public int GetAudioSessionId() {
		if (audioTrack == null)
			return -1;
		return audioTrack.getAudioSessionId();
	}

	int miAudioVolume = -96;

	public int GetAudioVolume() {
		return miAudioVolume;
	}

	private void CalcAudioVolume(byte[] bAudioData, int iAudioDataLen) {
		int ndb = 0;

		short value;

		int i;
		long v = 0;
		for (i = 0; i < iAudioDataLen; i += mChannelCount * 2) {
			value = (short) (bAudioData[i] + bAudioData[i + 1] * 0xff00);
			v += Math.abs(value);
		}

		v = v / (iAudioDataLen / (mChannelCount * 2));

		if (v != 0) {
			ndb = (int) (20.0 * Math.log10((double) v / 65535.0));
		} else {
			// ndb = -96;
			ndb = -60;
		}

		miAudioVolume = (int) ndb;
	}
	private int AnalyzeFormat(){
		mStreamIdxVideo = -1;
		mStreamIdxAudio = -1;
		
		int iTotalStream = ffw.GetTotalStreamNumber(miFormatCtx);
		Log.d(TAG + mObjTAG, "iTotalStream = " + iTotalStream);
		for(int i=0; i<iTotalStream; i++){
			int iCodecType = ffw.GetStreamCodecType(miFormatCtx, i);
			Log.d(TAG+ mObjTAG, " --> iCodecType = " + iCodecType);
			if(iCodecType == FFmpegDef.AVMEDIA_TYPE_VIDEO){
				mStreamIdxVideo = i;
				try {
					int iRetVal = GetVideoFormat();
					if(iRetVal != 0) return -1;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if(iCodecType == FFmpegDef.AVMEDIA_TYPE_AUDIO && mAudio == true){
				mStreamIdxAudio = i;
				GetAudioFormat();				
			}
		}
		if(mStreamIdxVideo == -1 && mStreamIdxAudio == -1) return -1;
		return 0;
	}		
	
	private int GetVideoFormat() throws Exception {
		int iIdx = mStreamIdxVideo;
		int iCodecId = ffw.GetStreamCodecId(miFormatCtx, iIdx);
		String mime = "video/avc";
		if(iCodecId == FFmpegDef.AV_CODEC_ID_H264) mime = "video/avc";
		Log.d(TAG+ mObjTAG, "iCodecId="+iCodecId);
		
		int width = ffw.GetStreamWidth(miFormatCtx, iIdx);					
		int height = ffw.GetStreamHeight(miFormatCtx, iIdx);
		Log.d(TAG + mObjTAG, "width=" + width);
		Log.d(TAG + mObjTAG, "height=" + height);

		if(width == 0 || height == 0)
		{
			return -1;
		}

		float ffps = ffw.GetStreamFPS(miFormatCtx, iIdx);
		Log.d(TAG + mObjTAG, "ifps=" + ffps);
		
		formatVideo = MediaFormat.createVideoFormat(mime, width, height);
		
		int iExtraLen = ffw.GetStreamExtraDataLen(miFormatCtx, iIdx); 
		if(iExtraLen > 0){
			//ByteBuffer extBuf = ByteBuffer.allocate(iExtraLen);
			byte[] extBuf = new byte[iExtraLen];
			int iGetLen = ffw.GetStreamExtraData(miFormatCtx, iIdx, extBuf);
			//for(int j=0; j<iExtraLen; j++){
			//	Log.d(TAG, Byte.toString(extBuf[j]));
			//}
			Log.d(TAG + mObjTAG, "ExtraData=" + byte2HexStr(extBuf));
			Log.d(TAG + mObjTAG, "iGetLen=" + iGetLen);
			
			if((extBuf[0] != 0 || extBuf[1] != 0 || extBuf[2] != 0 || extBuf[3] != 1)){
				int i_csd0_len = extBuf[7];
				byte[] b_csd0 = new byte[i_csd0_len+4];
				b_csd0[0] = 0; b_csd0[1] = 0; b_csd0[2] = 0; b_csd0[3] = 1;
				System.arraycopy(extBuf, 7+1, b_csd0, 4, i_csd0_len);
				ByteBuffer extByteBuf_cds0 = ByteBuffer.wrap(b_csd0);
				formatVideo.setByteBuffer("csd-0", extByteBuf_cds0);
				
				int i_csd1_len = extBuf[7 + i_csd0_len + 3];
				byte[] b_csd1 = new byte[i_csd1_len+4]; 
				b_csd1[0] = 0; b_csd1[1] = 0; b_csd1[2] = 0; b_csd1[3] = 1;
				System.arraycopy(extBuf, 7+i_csd0_len+3+1, b_csd1, 4, i_csd1_len);
				ByteBuffer extByteBuf_cds1 = ByteBuffer.wrap(b_csd1);
				formatVideo.setByteBuffer("csd-1", extByteBuf_cds1);							
			}
			else{							
				ByteBuffer extByteBuf = ByteBuffer.wrap(extBuf);
				formatVideo.setByteBuffer("csd-0", extByteBuf);
			}
			Log.d(TAG + mObjTAG, "iExtraLen=" + iExtraLen);
		}			
		return 0;
	}
	
	/**
	 * The code profile, Sample rate, channel Count is used to
	 * produce the AAC Codec SpecificData.
	 * Android 4.4.2/frameworks/av/media/libstagefright/avc_utils.cpp refer
	 * to the portion of the code written.
	 * 
	 * MPEG-4 Audio refer : http://wiki.multimedia.cx/index.php?title=MPEG-4_Audio#Audio_Specific_Config
	 * 
	 * @param audioProfile is MPEG-4 Audio Object Types
	 * @param sampleRate
	 * @param channelConfig
	 * @return MediaFormat
	 */
	private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate, int channelConfig) {
		MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig);
		
	    int samplingFreq[] = {
	        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
	        16000, 12000, 11025, 8000
	    };
	    
	    // Search the Sampling Frequencies
	    int sampleIndex = -1;
	    for (int i = 0; i < samplingFreq.length; ++i) {
	    	if (samplingFreq[i] == sampleRate) {
				Log.d("TAG+ mObjTAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i);
	    		sampleIndex = i;
	    	}
	    }
	    
	    if (sampleIndex == -1) {
	    	return null;
	    }
	    
		ByteBuffer csd = ByteBuffer.allocate(2);
		csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));
		
		csd.position(1);
		csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (channelConfig << 3)));
		csd.flip();
		format.setByteBuffer("csd-0", csd); // add csd-0
		
		for (int k = 0; k < csd.capacity(); ++k) {
			byte bValue = csd.array()[k];
			Log.e("TAG+ mObjTAG", "csd : 0x" + Integer.toHexString(bValue));
		}
		
		return format;
	}
	
	private int GetAudioFormat(){
		int iIdx = mStreamIdxAudio;
		
		int iCodecId = ffw.GetStreamCodecId(miFormatCtx, iIdx);
		String mime = "audio/xxxxx";
		mbDecAudByMC = false;
		mbIsAAC = false;
		if(iCodecId == FFmpegDef.AV_CODEC_ID_AAC){
			mime = "audio/mp4a-latm";
			//mime = "audio/mp4a-aacextended";
			mbDecAudByMC = true;
			mbIsAAC = true;
		}
		if(iCodecId == FFmpegDef.AV_CODEC_ID_MP2 || iCodecId == FFmpegDef.AV_CODEC_ID_MP3){
			mime = "audio/mpeg";
			mbDecAudByMC = true;
		}				
		Log.d(TAG + mObjTAG, "iCodecId=" + iCodecId);
		
		mSampleRate = ffw.GetSampleRate(miFormatCtx, iIdx);					
		mChannelCount = ffw.GetChannels(miFormatCtx, iIdx);
		//if(mChannelCount > 2) mChannelCount = 2; //hacker 
		Log.d(TAG + mObjTAG, "mSampleRate=" + mSampleRate);
		Log.d(TAG + mObjTAG, "mChannelCount=" + mChannelCount);
		/*
		 * csj 2016-9-5
		 */
		if(mSampleRate <= 0 && mChannelCount <= 0){
			mStreamIdxAudio = -1;
			return -1;
		}
		int iRetVal = -1;
		if(mbDecAudByMC){
			int iExtraLen = ffw.GetStreamExtraDataLen(miFormatCtx, iIdx);
			if(mbIsAAC && iExtraLen <= 0){
				formatAudio = makeAACCodecSpecificData(MediaCodecInfo.CodecProfileLevel.AACObjectLC, mSampleRate, mChannelCount);
				//byte[] bytes = new byte[]{(byte) 0x12, (byte)0x10};
			    //ByteBuffer bb = ByteBuffer.wrap(bytes);
			    //formatAudio.setByteBuffer("csd-0", bb);				
			}
			else{
				formatAudio = MediaFormat.createAudioFormat(mime, mSampleRate, mChannelCount);
							
				if(iExtraLen > 0){
					//ByteBuffer extBuf = ByteBuffer.allocate(iExtraLen);
					byte[] extBuf = new byte[iExtraLen];
					int iGetLen = ffw.GetStreamExtraData(miFormatCtx, iIdx, extBuf);
					//for(int j=0; j<iExtraLen; j++){
					//	Log.d(TAG, Byte.toString(extBuf[j]));
					//}
					Log.d(TAG + mObjTAG, "ExtraData=" + byte2HexStr(extBuf));
					Log.d(TAG + mObjTAG, "iGetLen=" + iGetLen);
					
					ByteBuffer extByteBuf = ByteBuffer.wrap(extBuf);
					formatAudio.setByteBuffer("csd-0", extByteBuf);
				}
				Log.d(TAG + mObjTAG, "iExtraLen=" + iExtraLen);
			}
			iRetVal = 0;
		}
		else{
			iRetVal = ffw.OpenAudioDecoder(miFormatCtx, miAudioDecCtx, iIdx);
			Log.d(TAG + mObjTAG, "OpenAudioDecoder, iRetVal=" + iRetVal);
		}
 		
		return iRetVal;
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

	