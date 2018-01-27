package cn.lawwing.lwvideoplayer.widget;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import cn.lawwing.lwvideoplayer.MainActivity;
import cn.lawwing.lwvideoplayer.R;
import cn.lawwing.lwvideoplayer.utils.ThreadUtil;

/**
 * Created by lawwing on 2018/1/27.
 */

public class VideoPlayerView extends RelativeLayout implements TextureView.SurfaceTextureListener {
    private VLCMediaPlayer mMediaPlayer;
    private String mUrl = "http://192.168.0.101:8080/video/sp.mp4";

    private boolean isCreatedSurface;
    private Surface surface;

    private static final int MSG_LOAD_PROGRESS = 1; //加载进度条
    private static final int MSG_PREPARE_PLAY = 2;//已准备好
    private static final int MSG_START_PLAY = 3;//开始播放
    private static final int MSG_PLAY_FAIL = 4;//播放失败
    private static final int MSG_WAIT_SURFACE_CREATED = 5;//等待创建Surface
    private static final int MSG_DROP_FAIL = 6; //拖动失败

    private Context context;
    private TextureView videoView;

    public VideoPlayerView(Context context) {
        this(context, null);
    }

    public VideoPlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        View videoPlayView = LayoutInflater.from(context).inflate(R.layout.layout_video_play, this, true);

        initView(videoPlayView);
    }

    private void initView(View videoPlayView) {
        videoView = (TextureView) videoPlayView.findViewById(R.id.ttv_video);
        videoView.setSurfaceTextureListener(this);
    }

    public void startPlayVideo() {
        Log.e("lawwingLog", "startPlayVideo");
        if (!isCreatedSurface) {
            Log.e("lawwingLog", "!isCreatedSurface");
            mHandler.sendEmptyMessageDelayed(MSG_WAIT_SURFACE_CREATED, 500);
        } else {
            Log.e("lawwingLog", "isCreatedSurface");
            mHandler.removeMessages(MSG_WAIT_SURFACE_CREATED);
            ThreadUtil.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    if (mMediaPlayer == null) {
                        Log.e("lawwingLog", "mMediaPlayer");
                        mMediaPlayer = new VLCMediaPlayer(context);
                        mMediaPlayer.setTextureView(videoView, surface);
                        mMediaPlayer.setDataSource(mUrl);
                        mMediaPlayer.play();
                    }
                }
            });
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        Log.e("lawwingLog", "onSurfaceTextureAvailable");
        isCreatedSurface = true;
        surface = new Surface(surfaceTexture);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WAIT_SURFACE_CREATED:
                    startPlayVideo();
                    break;
                case MSG_LOAD_PROGRESS:
                    break;
            }
        }
    };
}
