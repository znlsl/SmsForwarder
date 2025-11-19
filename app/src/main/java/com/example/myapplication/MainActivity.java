package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 101;
    public static final String PREFS_NAME = "SmsForwarderPrefs";

    private EditText etSenderEmail, etSenderPassword, etReceiverEmail;
    private Button btnSave;
    private ListView lvLogs;

    private ArrayAdapter<String> logAdapter;
    private ArrayList<String> logMessages;

    public static Handler logHandler;

    private static class LogHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        LogHandler(MainActivity activity) {
            super(Looper.getMainLooper());
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                if (msg.obj instanceof String) {
                    activity.addLogMessage((String) msg.obj);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        etSenderEmail = findViewById(R.id.et_sender_email);
        etSenderPassword = findViewById(R.id.et_sender_password);
        etReceiverEmail = findViewById(R.id.et_receiver_email);
        btnSave = findViewById(R.id.btn_save);
        lvLogs = findViewById(R.id.lv_logs);

        // 初始化日志列表
        logMessages = new ArrayList<>();
        logAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, logMessages);
        lvLogs.setAdapter(logAdapter);

        // 初始化Handler
        logHandler = new LogHandler(this);

        // 加载配置
        loadSavedPreferences();
        checkAndRequestPermissions();

        // 设置按钮点击事件
        btnSave.setOnClickListener(v -> savePreferences());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除回调和消息，防止内存泄漏
        if (logHandler != null) {
            logHandler.removeCallbacksAndMessages(null);
            logHandler = null;
        }
    }

    private void addLogMessage(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        logAdapter.add(timestamp + ": " + message);
        // 保持日志列表最多显示100条
        if (logAdapter.getCount() > 100) {
            logAdapter.remove(logAdapter.getItem(0));
        }
        logAdapter.notifyDataSetChanged();
    }

    private void loadSavedPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        etSenderEmail.setText(prefs.getString("sender_email", ""));
        etSenderPassword.setText(prefs.getString("sender_password", ""));
        etReceiverEmail.setText(prefs.getString("receiver_email", ""));
        addLogMessage("配置已加载。");
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("sender_email", etSenderEmail.getText().toString().trim());
        editor.putString("sender_password", etSenderPassword.getText().toString().trim());
        editor.putString("receiver_email", etReceiverEmail.getText().toString().trim());
        editor.apply();
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        addLogMessage("配置已保存，等待新短信...");
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        String[] requiredPermissions = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
        };

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        // 仅在 Android 9 (API 28) 及以下版本才需要请求存储权限
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
            addLogMessage("正在请求权限...");
        } else {
            addLogMessage("权限检测通过，服务已就绪。");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                addLogMessage("权限申请成功，服务已就绪。");
            } else {
                addLogMessage("警告：部分权限被拒绝，功能可能无法正常使用。");
                Toast.makeText(this, "部分权限被拒绝，功能可能无法正常使用", Toast.LENGTH_LONG).show();
            }
        }
    }
}
