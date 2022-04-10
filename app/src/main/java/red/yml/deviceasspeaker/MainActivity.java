package red.yml.deviceasspeaker;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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
    private EditText mMsgToSend;
    private TextView mLog;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private ServerSocket mServer;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMsgToSend = findViewById(R.id.send_msg);
        mLog = findViewById(R.id.log_msg);
        HandlerThread thread = new HandlerThread("socket looper");
        thread.start();
        mHandler = new Handler(thread.getLooper());

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(9500)) {
                mServer = serverSocket;
                Log.i(TAG, "Connecting...");
                log("Connecting...");
                Socket client = mServer.accept();
                try (InputStream inputStream = client.getInputStream();
                     OutputStream outputStream = client.getOutputStream()) {
                    log("Connected.");
                    mInputStream = new BufferedInputStream(inputStream);
                    mOutputStream = new BufferedOutputStream(outputStream);

                    byte[] buffer = new byte[1024];
                    while (!Thread.interrupted()) {
                        int size = mInputStream.read(buffer);
                        if (size <= 0) {
                            break;
                        }
                        String msg = new String(buffer, 0, size, StandardCharsets.UTF_8);
                        log("接收: " + msg);
                    }
                    log("连接已断开。");
                }
            } catch (IOException e) {
                Log.e(TAG, "onCreate: ", e);
                log("Error: " + e.getLocalizedMessage());
            }
        }).start();
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