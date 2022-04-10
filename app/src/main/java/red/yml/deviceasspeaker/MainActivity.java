package red.yml.deviceasspeaker;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
//adb forward tcp:9500 tcp:9500
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int SAMPLE_RATE = 48000;
    private static final int MIN_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
    private EditText mMsgToSend;
    private TextView mLog;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private ServerSocket mServer;
    private Handler mHandler;
    private AudioTrack mAudioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMsgToSend = findViewById(R.id.send_msg);
        mLog = findViewById(R.id.log_msg);
        HandlerThread thread = new HandlerThread("socket looper");
        thread.start();
        mHandler = new Handler(thread.getLooper());
        mLog.append("MIN_BUFFER_SIZE = " + MIN_BUFFER_SIZE + "\n");

        mAudioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)//setUsage 设置 AudioTrack 的使用场景；
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)//setContentType 设置输入的音频文件内容的类型；
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)//采样格式
                        .setSampleRate(SAMPLE_RATE)//设置采样率
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)//设置声道
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(MIN_BUFFER_SIZE)
                .build();

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(9500)) {
                mServer = serverSocket;
                while (!Thread.interrupted()) {
                    Log.i(TAG, "Connecting...");
                    log("Connecting...");
                    Socket client = mServer.accept();
                    try (InputStream inputStream = client.getInputStream();
                         OutputStream outputStream = client.getOutputStream()) {
                        log("Connected.");
                        mInputStream = new BufferedInputStream(inputStream);
                        mOutputStream = new BufferedOutputStream(outputStream);

                        mOutputStream.write((MIN_BUFFER_SIZE + "").getBytes(StandardCharsets.UTF_8));
                        mOutputStream.flush();

                        mAudioTrack.play();

                        byte[] buffer = new byte[MIN_BUFFER_SIZE];
                        while (!Thread.interrupted()) {
                            int size = mInputStream.read(buffer);
                            if (size <= 0) {
                                break;
                            }
                            playStream(buffer, size);
                        }
                        log("连接已断开。");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "onCreate: ", e);
                log("Error: " + e.getLocalizedMessage());
            }
        }).start();
    }

    private void playStream(byte[] data, int len) {
        mAudioTrack.write(data, 0, len);
    }

    private void log(String msg) {
        runOnUiThread(() -> mLog.append(msg + "\n"));
    }

    public void sendMessage(View view) {
        String txt = mMsgToSend.getText().toString();
        mMsgToSend.setText("");
        mLog.append("发送: " + txt + "\n");
        mHandler.post(() -> sendMsg(txt));
    }

    private void sendMsg(String txt) {
        if (mOutputStream == null) {
            mLog.append("mOutputStream == null");
            return;
        }
        try {
            mOutputStream.write(txt.getBytes(StandardCharsets.UTF_8));
            mOutputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "sendMessage: ", e);
            log("发送失败。\n");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }
}