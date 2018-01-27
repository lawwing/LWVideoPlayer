package com.ffmpeg.intf;

public class FFmpegDef {
	public static final int AVMEDIA_TYPE_UNKNOWN = -1;  ///< Usually treated as AVMEDIA_TYPE_DATA
	public static final int AVMEDIA_TYPE_VIDEO = 0;
	public static final int AVMEDIA_TYPE_AUDIO = 1;
	public static final int AVMEDIA_TYPE_DATA = 2;          ///< Opaque data information usually continuous
	public static final int AVMEDIA_TYPE_SUBTITLE = 3;
	public static final int AVMEDIA_TYPE_ATTACHMENT = 4;    ///< Opaque data information usually sparse
	public static final int AVMEDIA_TYPE_NB = 5;
	
	public static final int AV_CODEC_ID_H264 = 28;
	
	public static final int AV_CODEC_ID_MP2 = 0x15000;
	public static final int AV_CODEC_ID_MP3 = 0x15001;
	public static final int AV_CODEC_ID_AAC = 0x15002;
	public static final int AV_CODEC_ID_AC3 = 0x15003;
	public static final int AV_CODEC_ID_DTS = 0x15004;
	public static final int AV_CODEC_ID_VORBIS = 0x15005;
	public static final int AV_CODEC_ID_DVAUDIO = 0x15006;
	public static final int AV_CODEC_ID_WMAV1 = 0x15007;
	public static final int AV_CODEC_ID_WMAV2 = 0x15008;
	
	public static final int AVSEEK_FLAG_BACKWARD = 1; ///< seek backward
	public static final int AVSEEK_FLAG_BYTE     = 2; ///< seeking based on position in bytes
	public static final int AVSEEK_FLAG_ANY      = 4; ///< seek to any frame, even non-keyframes
	public static final int AVSEEK_FLAG_FRAME    = 8; ///< seeking based on frame number	
}
