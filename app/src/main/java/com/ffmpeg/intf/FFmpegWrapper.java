/*
 * Copyright (c) 2013, David Brodsky. All rights reserved.
 *
 *	This program is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *	
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *	
 *	You should have received a copy of the GNU General Public License
 *	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ffmpeg.intf;

import java.nio.ByteBuffer;

/**
 * A wrapper around the FFmpeg C libraries
 * designed for muxing encoded AV packets
 * into various output formats not supported by
 * Android's MediaMuxer, which is currently limited to .mp4
 * 
 * As this is designed to complement Android's MediaCodec class,
 * the only supported formats for jData in writeAVPacketFromEncodedData are:
 * H264 (YUV420P pixel format) / AAC (16 bit signed integer samples, one center channel)
 * 
 * Methods of this class must be called in the following order:
 * 0. (optional) setAVOptions
 * 1. prepareAVFormatContext
 * 2. (repeat for each packet) writeAVPacketFromEncodedData
 * 3. finalizeAVFormatContext
 * @author davidbrodsky
 *
 */
public class FFmpegWrapper {

    static {
        System.loadLibrary("FFmpegWrapper");
    }

    public native void setAVOptions(AVOptions jOpts);
    public native void prepareAVFormatContext(String jOutputPath);
    public native void writeAVPacketFromEncodedData(ByteBuffer jData, int jIsVideo, int jOffset, int jSize, int jFlags, long jPts);
    public native void finalizeAVFormatContext();
    public native String weilTest(String inString);
    public native int PassOjbTest(jniPassOBJ jpo, ByteBuffer data);
    
    public native int Init();
    public native int CreateFormatCtx();
    public native int CreatePacket();
    public native int OpenInput(int FormatCtx, String filepath, int probesize);
    public native int CloseInput(int FormatCtx);
    public native int FindStreamInfo(int FormatCtx);
    public native int GetTotalStreamNumber(int FormatCtx);
    public native int GetStreamCodecId(int FormatCtx, int stream_index);
    public native int GetStreamCodecType(int FormatCtx, int stream_index);
    public native int GetStreamWidth(int FormatCtx, int stream_index);
    public native int GetStreamHeight(int FormatCtx, int stream_index); 
    public native float GetStreamFPS(int FormatCtx, int stream_index);
    public native int GetStreamExtraDataLen(int FormatCtx, int stream_index);
    //public native int GetStreamExtraData(int FormatCtx, int stream_index, ByteBuffer data);
    //public native int ReadFrame(int FormatCtx, FrameInfo jFrameInfo, ByteBuffer data);
    public native int GetStreamExtraData(int FormatCtx, int stream_index, byte[] data);
    public native int ReadFrame(int FormatCtx, int PacketHdl, FrameInfo jfi, byte[] data);
    public native int ReadFrameEx(int FormatCtx, int PacketHdl, FrameInfoEx jfiEx, byte[] data);
    
    public native int GetSampleRate(int FormatCtx, int stream_index);
    public native int GetChannels(int FormatCtx, int stream_index);
    public native int Flush(int FormatCtx);
    public native int CreateBSFilter(int FormatCtx);
    
    public native int OpenAudioDecoder(int FormatCtx, int AudioDecCtx, int stream_index);
    public native int CreateAudioDecCtx();
//    public native int ReadFrame3(int FormatCtx, int AudioDecCtx, int PacketHdl, FrameInfoEx jfiEx, byte[] data);
    public native int ReadFrame3(int FormatCtx, int AudioDecCtx, int PacketHdl, int timeout, FrameInfoEx jfiEx, byte[] data);
    
    public native int  SeekFile(int FormatCtx, int StreamIndex, long min_ts, long ts, long max_ts, int flags);
    public native long GetDuration(int FormatCtx);
    
    /**
     * Used to configure the muxer's options.
     * Note the name of this class's fields 
     * have to be hardcoded in the native method
     * for retrieval.
     * @author davidbrodsky
     *
     */
    static public class AVOptions{
    	public int videoHeight = 1280;
    	public int videoWidth = 720;
    	
    	public int audioSampleRate = 44100;
    	public int numAudioChannels = 1;
    	
    	public int hlsSegmentDurationSec = 10;
    }

    static public class jniPassOBJ{
    	public int int_p;
    	public String str_p;
    	public ByteBuffer jBuffer;
    }    
    
    static public class FrameInfo{
    	public int stream_index;
    	public int dts_h;
    	public int dts_l;
    	public int pts_h;
    	public int pts_l;
    }
    
    static public class FrameInfoEx{
    	public int stream_index;
    	public int dts_h;
    	public int dts_l;
    	public int pts_h;
    	public int pts_l;
    	public int flag;
    }    
}
