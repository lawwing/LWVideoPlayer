package cn.lawwing.lwvideoplayer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.util.VLCInstance;

/**
 * Created by Administrator on 2018/1/11 0011.
 * <p>
 * VLC媒体播放类
 */
public class VLCMediaPlayer {
    private static final String TAG = "VLCMediaPlayer";
    private Context mContext;
    private MediaPlayer mediaPlayer;
    private Media mMedia;
    private SurfaceView surfaceView;

    private LibVLC mLibVLC;
    private IVLCVout mIVLCVout;

    public VLCMediaPlayer(Context context) {
        this.mContext = context;
        mLibVLC = LibVLC(mContext);
        mediaPlayer = createMediaPlayer(mContext);
        mIVLCVout = mediaPlayer.getVLCVout();
    }

    public void setVideoView(SurfaceView sv) {
        surfaceView = sv;
        mIVLCVout.detachViews();
        mIVLCVout.setVideoView(surfaceView);
        mIVLCVout.attachViews();
    }

    public void setTextureView(TextureView textureView, Surface surface) {
        mIVLCVout.detachViews();
        mIVLCVout.setVideoView(textureView);
        if (null != surface) {
            mIVLCVout.setVideoSurface(surface, new SurfaceHolder() {
                @Override
                public void addCallback(Callback callback) {
                    Log.e("lawwing", "addCallback");
                }

                @Override
                public void removeCallback(Callback callback) {

                    Log.e("lawwing", "removeCallback");
                }

                @Override
                public boolean isCreating() {
                    Log.e("lawwing", "isCreating");
                    return false;
                }

                @Override
                public void setType(int type) {

                    Log.e("lawwing", "setType");
                }

                @Override
                public void setFixedSize(int width, int height) {
                    Log.e("lawwing", "setFixedSize");

                }

                @Override
                public void setSizeFromLayout() {

                    Log.e("lawwing", "setSizeFromLayout");
                }

                @Override
                public void setFormat(int format) {

                    Log.e("lawwing", "setFormat");
                }

                @Override
                public void setKeepScreenOn(boolean screenOn) {
                    Log.e("lawwing", "setKeepScreenOn");

                }

                @Override
                public Canvas lockCanvas() {
                    Log.e("lawwing", "lockCanvas");
                    return null;
                }

                @Override
                public Canvas lockCanvas(Rect dirty) {
                    Log.e("lawwing", "lockCanvas Rect");
                    return null;
                }

                @Override
                public void unlockCanvasAndPost(Canvas canvas) {

                    Log.e("lawwing", "unlockCanvasAndPost");
                }

                @Override
                public Rect getSurfaceFrame() {
                    Log.e("lawwing", "getSurfaceFrame");
                    return null;
                }

                @Override
                public Surface getSurface() {
                    Log.e("lawwing", "getSurface");
                    return null;
                }
            });
        }
        mIVLCVout.attachViews();
    }

    public void resetTexture(TextureView videoView, Surface surface) {

    }

    /**
     * 设置音量大小
     *
     * @param volume
     */
    public void setVolume(int volume) {
        mediaPlayer.setVolume(volume);
    }

    /**
     * 获取音量
     *
     * @return
     */
    public int getVolume() {
        return mediaPlayer.getVolume();
    }

    /**
     * 设置播放速度
     *
     * @param rate
     */
    public void setRate(float rate) {
        mediaPlayer.setRate(rate);
    }

    /**
     * 获取播放速度
     *
     * @return
     */
    public float getRate() {
        return mediaPlayer.getRate();
    }

    public void setDataSource(String playPaht) {
        setDataSource(Uri.parse(playPaht));
    }

    public void setDataSource(Uri uri) {
        mMedia = new Media(VLCInstance.get(mContext), uri);
        mediaPlayer.setMedia(mMedia);
    }

    /**
     * 获取当前时间
     *
     * @return
     */
    public long getTime() {
        return mediaPlayer.getTime();
    }

    /**
     * 获取总时长
     *
     * @return
     */
    public long getDuration() {
        return mMedia.getDuration();
    }

    public void play() {
        mediaPlayer.play();
    }

    public void stop() {
        mediaPlayer.stop();
        //释放资源
        mediaPlayer.release();
    }

    public void pause() {
        mediaPlayer.pause();
    }

    public void seekTo(long seekTo) {
        mediaPlayer.setTime(seekTo);
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }


    private LibVLC LibVLC(Context context) {
        return VLCInstance.get(context);
    }

    private MediaPlayer createMediaPlayer(Context context) {
        final MediaPlayer mp = new MediaPlayer(mLibVLC);
        return mp;
    }

    public void addCallback(Callback Callback) {
        mediaPlayer.getVLCVout().addCallback(Callback);
    }

    public void setOnHardwareAccelerationError(OnHardwareAccelerationError error) {
        mLibVLC.setOnHardwareAccelerationError(error);
    }

    public void setEventListener(EventListener listener) {
        mediaPlayer.setEventListener(listener);
    }


    public interface OnHardwareAccelerationError extends LibVLC.HardwareAccelerationError {
    }

    public interface Callback extends IVLCVout.Callback {
    }

    public interface EventListener extends MediaPlayer.EventListener {
    }
}
