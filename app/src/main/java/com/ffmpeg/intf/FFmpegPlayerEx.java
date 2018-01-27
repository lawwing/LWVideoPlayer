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

import com.ffmpeg.intf.FFmpegWrapper.FrameInfoEx;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FFmpegPlayerEx {
	private final String TAG = "FFmpegPlayerEx";
	private Surface mSurface;
	private FrameInfoEx fi = new FrameInfoEx();
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
	public int mStreamIdxVideo = -1;
	public int mStreamIdxAudio = -1;
	
	private final int MAX_TRY_TIMES = 3;	 	
	private int miState;
	public static final int ST_Idle = 1;
	public static final int ST_Opening = 1;
	public static final int ST_Playing = 2;
	public static final int ST_RecvEOF = 3;
	public static final int ST_GotFormat = 4;
	public static final int ST_F_Create = -1;
	public static final int ST_F_Open = -2;
	public static final int ST_F_AnalyzeFmt = -3;
	public static final int ST_F_StartVideo = -4;
	public static final int ST_NetFail = -5;		
	
	private MediaCodec mcAudioDec;
	private MediaCodec mcVideoDec;
	private AudioTrack audioTrack;
	private final int MAX_VIDEO_PACKET = 150*1024;
	private final int MAX_VIDEO_NUM = 60;
	private final int MAX_AUDIO_PACKET = 10*1024;
	private final int MAX_AUDIO_NUM = 60;
	
	private BlockingQueue<Integer> mQVIdle = new LinkedBlockingQueue<Integer>();
	private BlockingQueue<Integer> mQVOcp = new LinkedBlockingQueue<Integer>();

	private BlockingQueue<Integer> mQAIdle = new LinkedBlockingQueue<Integer>();
	private BlockingQueue<Integer> mQAOcp = new LinkedBlockingQueue<Integer>();
		
	private List<AVData> avDatVideo = new ArrayList<AVData>();
	private List<AVData> avDatAudio = new ArrayList<AVData>();
	
	MediaFormat formatVideo = null;
	MediaFormat formatAudio = null;
//	FragmentTabHost f;
	private int mSampleRate;					
	private int mChannelCount; 
	private boolean mbFlushBeforePlay = false; 
	
	private boolean mInteraction = false;
	
	public int GetState(){
		return miState;
	}
	
	public int SetDataSource(String url, int probesize, boolean interaction){
		mUrl = url;
		mProbesize = probesize;
		mInteraction = interaction;
		return 0;
	}
	//chensj 16-5-13
	public void setSurface(Surface surface){
		this.mSurface = surface;
	}
	
	/*public FFmpegPlayerEx(Context cContext, Surface surface){
		mContext = cContext;
		mSurface = surface;
		
		mbStopFlag = false;		
		if(mavDataAry == null) mavDataAry = new byte[MAX_VIDEO_PACKET]; 
		miState = ST_Idle;
		
		int i;
		for(i=0; i<MAX_VIDEO_NUM; i++){
			AVData av = new AVData();
			av.dts = 0;
			av.SampleSize = 0;
			av.avd = new byte[MAX_VIDEO_PACKET];
			avDatVideo.add(av);
		}
		
		for(i=0; i<MAX_AUDIO_NUM; i++){
			AVData av = new AVData();
			av.dts = 0;
			av.SampleSize = 0;
			av.avd = new byte[MAX_AUDIO_PACKET];
			avDatAudio.add(av);
		}		
	}*/
	/*
	 * chensj 16-5-17
	 */
	public FFmpegPlayerEx(Context cContext, Surface surface){
		mContext = cContext;
		mSurface = surface;
		
		mbStopFlag = false;		
		if(mavDataAry == null) mavDataAry = new byte[MAX_VIDEO_PACKET]; 
		miState = ST_Idle;
		
		int i;
		for(i=0; i<MAX_VIDEO_NUM; i++){
			AVData av = new AVData();
			av.dts = 0;
			av.SampleSize = 0;
			av.avd = new byte[MAX_VIDEO_PACKET];
			avDatVideo.add(av);
		}
		
		for(i=0; i<MAX_AUDIO_NUM; i++){
			AVData av = new AVData();
			av.dts = 0;
			av.SampleSize = 0;
			av.avd = new byte[MAX_AUDIO_PACKET];
			avDatAudio.add(av);
		}		
	}	
	public int Start(boolean bFlushBeforePlay){
		mbStopFlag = false;
		mStreamIdxVideo = -1;
		mStreamIdxAudio = -1;
		mSampleRate = 0;					
		mChannelCount = 0;	
		miState = ST_Opening;
		mbFlushBeforePlay = bFlushBeforePlay;		
		int i;
		mQVIdle.clear();
		mQAIdle.clear();
		for(i=0; i<MAX_VIDEO_NUM; i++) mQVIdle.add(i); 
		for(i=0; i<MAX_AUDIO_NUM; i++) mQAIdle.add(i);
		mQVOcp.clear();
		mQAOcp.clear();
		
//		Log.d(TAG, "mQVIdle.size()="+mQVIdle.size());
//		for(i=0; i<MAX_VIDEO_NUM; i++){			
//			Object obj = mQVIdle.poll();
//			Log.d(TAG, "obj="+obj);
//			int iIdx = (Integer) obj;
//			Log.d(TAG, "iIdx="+iIdx);
//		}
		
		m_ldts = -1;
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
		int i;
		for(i=0; i<MAX_VIDEO_NUM; i++){
			AVData av = avDatVideo.get(i);
			av.avd = null;
		}
		avDatVideo.clear();
		avDatVideo = null;
		
		for(i=0; i<MAX_AUDIO_NUM; i++){
			AVData av = avDatAudio.get(i);
			av.avd = null;
		}
		avDatAudio.clear();
		avDatAudio = null;
		
		mQVIdle.clear();
		mQAIdle.clear();
		mQVOcp.clear();
		mQAOcp.clear();		
		
		mQVIdle = null;
		mQAIdle = null;
		mQVOcp = null;
		mQAOcp = null;		
		return 0;
	}
	
	private class AVData{
		public long dts;
		public byte[] avd;
		public int SampleSize;
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
			Log.d(TAG, "OpenInput >>> iRetVal="+iRetVal);
			if(iRetVal != 0){
				miState = ST_F_Open;
				Log.d(TAG, "OpenInput fail. iRetVal="+iRetVal);
				return;
			}
			
			for(i=0; i<MAX_TRY_TIMES; i++){
				Log.d(TAG, "FindStreamInfo >>> i="+i);
				iRetVal = ffw.FindStreamInfo(miFormatCtx);
				Log.d(TAG, "FindStreamInfo >>> iRetVal="+iRetVal+"; mProbesize--> "+mProbesize);
				if(iRetVal == 0 || mProbesize==-1) break;
			}	
			Log.d(TAG, "FindStreamInfo >>> iRetVal="+iRetVal);
			if(iRetVal != 0){
				Log.d(TAG, "FindStreamInfo fail. iRetVal="+iRetVal);
				ffw.CloseInput(miFormatCtx); //2016.04.29
				return;
			}
			
			ffw.CreateBSFilter(miFormatCtx);
			
			iRetVal = AnalyzeFormat();
			if(iRetVal != 0){
				miState = ST_F_AnalyzeFmt;
				ffw.CloseInput(miFormatCtx);
				return;
			}
			miState = ST_GotFormat;
						
			if(mStreamIdxVideo != -1) new VideoThread().start();
			if(mStreamIdxAudio != -1) new AudioThread().start();
			
			/////////////
			if(mbFlushBeforePlay && mInteraction == false) ffw.Flush(miFormatCtx);
			
			
			int iReceiveFailCounter = 0;
			long iCounter = 0;
			boolean bGotIFrame = false;
			if(mbFlushBeforePlay==false) bGotIFrame = true;
			while(mbStopFlag == false){
				int sampleSize = -1;
				sampleSize = ffw.ReadFrameEx(miFormatCtx, miPacketHdl, fi, mavDataAry);
				/*if(iCounter % 100 == 0){
					Log.d(TAG, "iCounter = "+iCounter);
				}
				iCounter++;*/
				if(sampleSize < 0){
					//miState = ST_RecvEOF;					
					//return;
					//ffw.Flush(miFormatCtx);
					iReceiveFailCounter++;
					if(iReceiveFailCounter > 20){
						miState = ST_NetFail; 
						break;
					}
					try {
                        Thread.sleep(5);} catch (InterruptedException e) {}
					continue;
				}
				iReceiveFailCounter = 0;
				
				if(fi.stream_index == mStreamIdxVideo && fi.flag == 1) bGotIFrame = true;
				if(bGotIFrame==false) continue;
				byte[] bt = new byte[54];
				System.arraycopy(mavDataAry, 0, bt, 0, 54);
				
				//Log.d(TAG, fi.stream_index+", iCounter="+(iCounter++)+", "+byte2HexStr(bt));
				
				long dts = fi.dts_h * 0xffFFffFF + fi.dts_l;									
				if(m_ldts == -1) m_ldts = dts;
				dts = dts - m_ldts;
				
				if(fi.stream_index == mStreamIdxVideo){
					while(!Thread.interrupted() && mbStopFlag==false){
						if(mQVIdle.size() < 1){
							if(mbFlushBeforePlay){
								Log.d(TAG, "m v-full dts="+dts);
								break; //满就直接丢掉
							}
							else{
								try {sleep(10);} catch (InterruptedException e) {}
								continue;								
							}
						}
						int iIdx = mQVIdle.poll();						
						AVData av = avDatVideo.get(iIdx);
						av.dts = dts;
						av.SampleSize = sampleSize;						
						System.arraycopy(mavDataAry, 0, av.avd, 0, sampleSize);
						//avDatVideo.set(iIdx, av);
						//Log.d(TAG, "m iIdx="+iIdx+" dts="+dts+", SampleSize="+sampleSize);
						mQVOcp.offer(iIdx);
						break;
					}
				}								
				else if(fi.stream_index == mStreamIdxAudio){
					while(!Thread.interrupted() && mbStopFlag==false){
						if(mQAIdle.size() < 1){
							if(mbFlushBeforePlay){
								Log.d(TAG, "m a-full dts="+dts);
								break; //满就直接丢掉
							}
							else{
								try {sleep(10);} catch (InterruptedException e) {}
								continue;								
							}
						}
					
						int iIdx = mQAIdle.poll();
						AVData av = avDatAudio.get(iIdx);
						av.dts = dts;
						av.SampleSize = sampleSize;
						System.arraycopy(mavDataAry, 0, av.avd, 0, sampleSize);
						//avDatAudio.set(iIdx, av);
						mQAOcp.offer(iIdx); 
						break;
					}
				}					
			}//end of while				
			ffw.CloseInput(miFormatCtx);
			Log.d(TAG, "PlayerThread over");
		}
								
	}//end of PlayerThread
	
	public int GetAudioSessionId(){
		if(audioTrack == null) return -1;
		return audioTrack.getAudioSessionId();		
	}

	int miAudioVolume = -96;
	public int GetAudioVolume(){
		return miAudioVolume;
	}
	
	private void CalcAudioVolume(byte[] bAudioData, int iAudioDataLen){
		int ndb = 0;

	    short value;

	    int i;
	    long v = 0;
	    for(i=0; i<iAudioDataLen; i+=mChannelCount*2)
	    {   
	    	value = (short) (bAudioData[i] + bAudioData[i+1] * 0xff00); 
	        v += Math.abs(value);
	    }   

	    v = v/(iAudioDataLen/(mChannelCount*2));

	    if(v != 0) {
	        ndb = (int)(20.0* Math.log10((double)v / 65535.0 ));
	    }   
	    else {
	        //ndb = -96;
	    	ndb = -60;
	    }   
	    
        miAudioVolume = (int) ndb;        
	}
	
	private class AudioThread extends Thread {
		public void run() {
			try {
				mcAudioDec = MediaCodec.createDecoderByType(formatAudio.getString(formatAudio.KEY_MIME));
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			mcAudioDec.configure(formatAudio, null, null, 0);
			Log.d(TAG, "audio_stream_index="+mStreamIdxAudio);
			
			mcAudioDec.start();
			int channelConfiguration = mChannelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
	        int minSize = AudioTrack.getMinBufferSize( mSampleRate, channelConfiguration, AudioFormat.ENCODING_PCM_16BIT);
	        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, channelConfiguration,
	        		AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
	        audioTrack.play();
	        
			ByteBuffer[] inputBuffers = mcAudioDec.getInputBuffers();
			ByteBuffer[] outputBuffers = mcAudioDec.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			long startMs = System.currentTimeMillis();
			final long kTimeOutUs = 1000;

			int iCounter = 0;
			byte[] avDataAry = new byte[MAX_AUDIO_PACKET];
			int sampleSize = -1;
			long dts = 0;
			int inIndex = -1;
			byte[] chunk = new byte[MAX_AUDIO_PACKET * 4];
			while (!Thread.interrupted() && mbStopFlag==false) {
				if(inIndex == -1) inIndex = mcAudioDec.dequeueInputBuffer(1000);				
				if (inIndex >= 0) {
					int iIdx = -1;
					try{iIdx = mQAOcp.poll(10, TimeUnit.MILLISECONDS);}catch(Exception e){}
					if(iIdx != -1){
						AVData av = avDatAudio.get(iIdx);
						sampleSize = av.SampleSize;
						dts = av.dts;
						System.arraycopy(av.avd, 0, avDataAry, 0, sampleSize);
						//Log.d(TAG, "a iIdx="+iIdx+" dts="+dts+", SampleSize="+sampleSize);
						mQAIdle.offer(iIdx);							
						iCounter++;
											
						ByteBuffer buffer = inputBuffers[inIndex];
						int i_buf_cap = buffer.capacity();
						if(i_buf_cap < sampleSize){ 
							Log.d(TAG, "--------------------------------sampleSize="+sampleSize+", i_buf_cap="+i_buf_cap);
						}
						buffer.clear();	
						buffer.put(avDataAry, 0, sampleSize);						
						buffer.flip();
						
						//byte[] bt = new byte[54];
						//System.arraycopy(avDataAry, 0, bt, 0, 54);
						//Log.d(TAG, "iCounter="+iCounter+", "+byte2HexStr(bt));
						
						mcAudioDec.queueInputBuffer(inIndex, 0, sampleSize, dts, 0);  
						inIndex = -1;
					}
				}

				int res = -1;
				try{res = mcAudioDec.dequeueOutputBuffer(info, kTimeOutUs);}catch(Exception e){}
	            if (res >= 0) {
	                int outputBufIndex = res;
	                ByteBuffer buf = outputBuffers[outputBufIndex];
	                
	                buf.get(chunk, 0, info.size);
	                buf.clear();
	                if(chunk.length > 0){    
	                	CalcAudioVolume(chunk, info.size); 
	                	audioTrack.write(chunk, 0, info.size);
	                	/*if(this.state.get() != PlayerStates.PLAYING) {
	                		if (events != null) handler.post(new Runnable() { @Override public void run() { events.onPlay();  } }); 
	            			state.set(PlayerStates.PLAYING);
	                	}*/
	                	
	                }
	                mcAudioDec.releaseOutputBuffer(outputBufIndex, false);
	                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
	                    Log.d(TAG, "saw output EOS.");
	                }
	            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
	            	outputBuffers = mcAudioDec.getOutputBuffers();
	                Log.d(TAG, "output buffers have changed.");
	            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
	                MediaFormat oformat = mcAudioDec.getOutputFormat();
	                Log.d(TAG, "output format has changed to " + oformat);
	            } else {
	                //Log.d(TAG, "dequeueOutputBuffer returned " + res);
	            }

				// All decoded frames have been rendered, we can stop playing now
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}
			}

			mcAudioDec.stop();
			mcAudioDec.release();
			audioTrack.stop();
			audioTrack.release();
			Log.d(TAG, "AudioThread over");
		}
	}
	
	private class VideoThread extends Thread {
		public void run() {
			try{
				mcVideoDec = MediaCodec.createDecoderByType(formatVideo.getString(formatVideo.KEY_MIME));
				mcVideoDec.configure(formatVideo, mSurface, null, 0);	 
				Log.d(TAG, "video_stream_index="+mStreamIdxVideo);
				mcVideoDec.start();
			}
			catch(Exception e){
				e.printStackTrace();
				miState = ST_F_StartVideo;
				return;
			}
						
			ByteBuffer[] inputBuffers = mcVideoDec.getInputBuffers();
			ByteBuffer[] outputBuffers = mcVideoDec.getOutputBuffers();
			BufferInfo info = new BufferInfo();
			long startMs = System.currentTimeMillis();

			int iCounter = 0;
			byte[] avDataAry = new byte[MAX_VIDEO_PACKET];
			int sampleSize = -1;
			long dts = 0;			
			int inIndex = -1;
			while (!Thread.interrupted() && mbStopFlag==false) {
				try{
				if(inIndex == -1) inIndex = mcVideoDec.dequeueInputBuffer(1000);
				
				if (inIndex >= 0) {
					int iIdx = -1;
					try{iIdx = mQVOcp.poll(10, TimeUnit.MILLISECONDS);}catch(Exception e){}
					
					if(iIdx != -1){
						AVData av = avDatVideo.get(iIdx);
						sampleSize = av.SampleSize;
						dts = av.dts;
						System.arraycopy(av.avd, 0, avDataAry, 0, sampleSize);
						//Log.d(TAG, "v iIdx="+iIdx+" dts="+dts+", SampleSize="+sampleSize);
						mQVIdle.offer(iIdx);							
						iCounter++;
											
						ByteBuffer buffer = inputBuffers[inIndex];
						int i_buf_cap = buffer.capacity();
						if(i_buf_cap < sampleSize){ 
							Log.d(TAG, "---------sampleSize="+sampleSize+", i_buf_cap="+i_buf_cap);
						}
						buffer.clear();	
						buffer.put(avDataAry, 0, sampleSize);						
						buffer.flip();
						
						//byte[] bt = new byte[54];
						//System.arraycopy(avDataAry, 0, bt, 0, 54);
						//Log.d(TAG, "iCounter="+iCounter+", "+byte2HexStr(bt));
						
						try{mcVideoDec.queueInputBuffer(inIndex, 0, sampleSize, dts, 0);}catch(Exception e){break;}
						inIndex = -1;
					}
				}
				}catch(Exception e){break;}

				int outIndex = -1;
				try{outIndex = mcVideoDec.dequeueOutputBuffer(info, 1000);}catch(Exception e){break;}
				switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
					outputBuffers = mcVideoDec.getOutputBuffers();
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					Log.d(TAG, "New format " + mcVideoDec.getOutputFormat());
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					//Log.d(TAG, "dequeueOutputBuffer timed out! iCounter="+iCounter);
					break;
				default:
					ByteBuffer buffer = outputBuffers[outIndex];
					//Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);
					//Log.v(TAG, "info.presentationTimeUs="+info.presentationTimeUs);

					// We use a very simple clock to keep the video FPS, or the video
					// playback will be too fast
					while ((info.presentationTimeUs / 1000) > (System.currentTimeMillis() - startMs)) {
						try {sleep(10);} catch (InterruptedException e) {break;}
					}
					try{mcVideoDec.releaseOutputBuffer(outIndex, true);}catch(Exception e){break;}
					break;
				}

				// All decoded frames have been rendered, we can stop playing now
				if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
					break;
				}
			}

			mcVideoDec.stop();
			mcVideoDec.release();
			Log.d(TAG, "VideoThread over");
		}
	}
	
	private int AnalyzeFormat(){
		mStreamIdxVideo = -1;
		mStreamIdxAudio = -1;
		
		int iTotalStream = ffw.GetTotalStreamNumber(miFormatCtx);
		Log.d(TAG, "iTotalStream="+iTotalStream);
		for(int i=0; i<iTotalStream; i++){
			int iCodecType = ffw.GetStreamCodecType(miFormatCtx, i);
			if(iCodecType == FFmpegDef.AVMEDIA_TYPE_VIDEO){
				mStreamIdxVideo = i;
				GetVideoFormat();
			}
			if(iCodecType == FFmpegDef.AVMEDIA_TYPE_AUDIO){
				mStreamIdxAudio = i;
				GetAudioFormat();				
			}
		}
		if(mStreamIdxVideo == -1 && mStreamIdxAudio == -1) return -1;
		return 0;
	}		
	
	private int GetVideoFormat(){
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
		
		formatVideo = MediaFormat.createVideoFormat(mime, width, height);
		
		int iExtraLen = ffw.GetStreamExtraDataLen(miFormatCtx, iIdx); 
		if(iExtraLen > 0){
			//ByteBuffer extBuf = ByteBuffer.allocate(iExtraLen);
			byte[] extBuf = new byte[iExtraLen];
			int iGetLen = ffw.GetStreamExtraData(miFormatCtx, iIdx, extBuf);
			/*for(int j=0; j<iExtraLen; j++){
				Log.d(TAG, Byte.toString(extBuf[j]));
			}*/
			Log.d(TAG, "ExtraData="+byte2HexStr(extBuf));
			Log.d(TAG, "iGetLen="+iGetLen);
			
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
			Log.d(TAG, "iExtraLen="+iExtraLen);
		}			
		return 0;
	}
	
	private int GetAudioFormat(){
		int iIdx = mStreamIdxAudio;
		
		int iCodecId = ffw.GetStreamCodecId(miFormatCtx, iIdx);
		String mime = "audio/mp4a-latm";
		if(iCodecId == FFmpegDef.AV_CODEC_ID_AAC) mime = "audio/mp4a-latm";
		Log.d(TAG, "iCodecId=0x"+ Integer.toHexString(iCodecId));
		
		mSampleRate = ffw.GetSampleRate(miFormatCtx, iIdx);					
		mChannelCount = ffw.GetChannels(miFormatCtx, iIdx);
		//if(mChannelCount > 2) mChannelCount = 2; //hacker 
		Log.d(TAG, "mSampleRate="+mSampleRate);
		Log.d(TAG, "mChannelCount="+mChannelCount);
							
		formatAudio = MediaFormat.createAudioFormat(mime, mSampleRate, mChannelCount);
		
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
			formatAudio.setByteBuffer("csd-0", extByteBuf);
		}
		Log.d(TAG, "iExtraLen="+iExtraLen);
 		
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

	