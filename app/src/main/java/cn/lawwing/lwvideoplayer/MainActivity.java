package cn.lawwing.lwvideoplayer;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.lawwing.lwvideoplayer.widget.VideoPlayerView;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.fl_video_play)
    FrameLayout videoLayout;

    private VideoPlayerView videoPlayerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initLayout();
    }

    private void initLayout() {
        videoLayout.removeAllViews();
        videoPlayerView = new VideoPlayerView(MainActivity.this);
        videoLayout.addView(videoPlayerView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        videoPlayerView.startPlayVideo();
    }

}
