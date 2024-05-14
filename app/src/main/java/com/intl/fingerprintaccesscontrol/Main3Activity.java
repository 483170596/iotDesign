package com.intl.fingerprintaccesscontrol;


import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.FileInputStream;
import java.util.Timer;
import java.util.TimerTask;

public class Main3Activity extends Activity {
    public static final String TAG = "Main3Activity";
    //todo 组件声明
    View clock;
    View weather;
    TextView curtmp;
    Timer timer_tmp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        //todo 获取组件实例
        final VideoView videoView = findViewById(R.id.videoView);
        weather = findViewById(R.id.weather);
        clock = findViewById(R.id.clock);
        curtmp = findViewById(R.id.curtmp);

        //todo 设置视频播放
        String path = Environment.getExternalStorageDirectory().getPath() + "/video.3gp";
        System.out.println(path);
        videoView.setVideoPath(path);
        videoView.start();
        //设置点击事件，OnClickListener不好用
        videoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Toast.makeText(getApplicationContext(), videoView.isPlaying() ? "暂停" : "继续", Toast.LENGTH_SHORT).show();
                if (videoView.isPlaying()) {
                    videoView.pause();
                } else {
                    videoView.start();
                }
                return false;
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.start();
                mp.setLooping(true);
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    //todo 点击事件
    public void onClick_Event(View v) {
        switch (v.getId()) {
            case R.id.weather: {
                weather.setVisibility(View.GONE);
                clock.setVisibility(View.VISIBLE);
                break;
            }
            case R.id.clock: {
                clock.setVisibility(View.GONE);
                weather.setVisibility(View.VISIBLE);
                break;
            }
            case R.id.exit: {
                //todo
                System.out.println("===================>exit");
                finish();
                break;
            }
        }
    }
}

