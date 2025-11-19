package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailService extends Service {

    private static final String TAG = "EmailService";
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    public static final String ACTION_UPDATE_LOG = "com.example.myapplication.UPDATE_LOG";
    public static final String EXTRA_LOG_MESSAGE = "log_message";

    @Override
    public void onCreate() {
        super.onCreate();
        log("EmailService: 服务已创建。");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // 构建前台通知，这是服务启动且不被系统立即杀死的关键
        Notification notification;
        try {
            notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("短信转发服务")
                    .setContentText("正在处理短信转发任务...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            // 启动前台服务，显示通知
            startForeground(1, notification);
            log("EmailService: 前台服务已启动，正在显示通知。");
        } catch (ForegroundServiceStartNotAllowedException e) {
            log("EmailService: 前台服务启动被拒绝 (Android 12+ 限制): " + e.getMessage());
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        // 如果有短信内容传来，则处理发送
        if (intent != null && intent.hasExtra("content")) {
            final String sender = intent.getStringExtra("sender");
            final String content = intent.getStringExtra("content");
            log("EmailService: 接收到任务，准备转发来自 " + sender + " 的短信。");
            // 开启新线程执行网络操作
            new Thread(() -> sendEmail(sender, content, startId)).start();
        } else {
            // 如果没有任务（可能是系统异常重启了服务），直接停止，避免空挂导致超时崩溃
            log("EmailService: 无待处理任务，停止服务。");
            stopSelf(startId);
        }

        return START_NOT_STICKY; // 任务完成后停止，不需要保持粘性
    }

    private void sendEmail(final String smsSender, final String smsContent, final int startId) {
        try {
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            final String fromEmail = prefs.getString("sender_email", "");
            final String fromPassword = prefs.getString("sender_password", "");
            final String toEmail = prefs.getString("receiver_email", "");

            if (fromEmail.isEmpty() || fromPassword.isEmpty() || toEmail.isEmpty()) {
                log("EmailService: 邮件发送失败，配置不完整。");
                return;
            }

            try {
                log("EmailService: 正在连接邮件服务器...");
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.qq.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");
                // 添加超时设置，防止网络问题导致服务长时间运行无法停止
                props.put("mail.smtp.connectiontimeout", "10000");
                props.put("mail.smtp.timeout", "10000");
                props.put("mail.smtp.writetimeout", "10000");

                Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(fromEmail, fromPassword);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(fromEmail));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject("收到来自 [" + smsSender + "] 的新短信");
                message.setText("发件人: " + smsSender + "\n\n" + "短信内容:\n" + smsContent);

                Transport.send(message);
                log("EmailService: 邮件发送成功！");

            } catch (MessagingException e) {
                log("EmailService: 邮件发送失败: " + e.getMessage());
                Log.e(TAG, "邮件发送异常详情: ", e);
            }
        } finally {
            // 关键修复：任务完成（无论成功失败）后必须停止服务
            // 否则会导致 Android 14+ 抛出 ForegroundServiceDidNotStopInTimeException
            log("EmailService: 任务结束，停止服务以释放资源。");
            stopSelf(startId);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("EmailService: 服务已完全停止。");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "短信转发服务通道",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    // 辅助方法，用于发送日志广播和打印Logcat
    private void log(String message) {
        Log.d(TAG, message);
        if (MainActivity.logHandler != null) {
            // 必须使用全限定名，以避免和 javax.mail.Message 冲突
            android.os.Message msg = MainActivity.logHandler.obtainMessage();
            msg.obj = message;
            MainActivity.logHandler.sendMessage(msg);
        }
    }
}
