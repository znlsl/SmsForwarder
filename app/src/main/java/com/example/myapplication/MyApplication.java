package com.example.myapplication;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    private static final String LOG_FILE_PREFIX = "sms_forwarder_crash_";
    private static final String LOG_FILE_SUFFIX = ".txt";

    @Override
    public void onCreate() {
        super.onCreate();
        // 设置全局未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            handleUncaughtException(e);
            // 记录日志后，让应用正常崩溃或退出
            System.exit(1);
        });
    }

    private void handleUncaughtException(Throwable e) {
        if (e == null) {
            return;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        String stackTrace = sw.toString();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = "********** CRASH REPORT **********\n" +
                "Timestamp: " + timestamp + "\n" +
                "Error: " + e.getMessage() + "\n" +
                "Stack Trace:\n" +
                stackTrace +
                "************************************\n\n";

        Log.e(TAG, "应用崩溃! 正在保存日志到文件。");
        Log.e(TAG, logMessage);

        String fileNameTimestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
        String logFileName = LOG_FILE_PREFIX + fileNameTimestamp + LOG_FILE_SUFFIX;

        saveLogToFile(logFileName, logMessage);
    }

    private void saveLogToFile(String fileName, String logMessage) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 安卓10及以上版本：使用 MediaStore API，无需权限即可保存到 Downloads
                ContentResolver resolver = getApplicationContext().getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri fileUri = resolver.insert(collection, contentValues);

                if (fileUri != null) {
                    try (OutputStream os = resolver.openOutputStream(fileUri)) {
                        if (os != null) {
                            os.write(logMessage.getBytes());
                            Log.d(TAG, "崩溃日志已通过 MediaStore 保存到 Downloads 目录。");
                        }
                    }
                } else {
                    Log.e(TAG, "通过 MediaStore 保存日志失败: Uri is null");
                }
            } else {
                // 安卓9及以下版本：使用传统方式，需要 WRITE_EXTERNAL_STORAGE 权限
                File logDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
                File logFile = new File(logDir, fileName);
                FileOutputStream fos = new FileOutputStream(logFile); // 不再追加，每次都是新文件
                fos.write(logMessage.getBytes());
                fos.close();
                Log.d(TAG, "崩溃日志已保存到 " + logFile.getAbsolutePath());
            }
        } catch (Exception ex) {
            Log.e(TAG, "保存崩溃日志失败", ex);
        }
    }
}