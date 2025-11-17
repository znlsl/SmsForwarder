package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.provider.Telephony;
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

    private SmsReceiver smsReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        // 服务创建时，动态注册短信接收器
        smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        registerReceiver(smsReceiver, filter);
        log("服务已创建并注册短信监听。");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("短信转发服务")
                .setContentText("服务正在后台运行...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
        log("前台服务已启动。");

        // 如果有短信内容传来，则处理发送
        if (intent != null && intent.hasExtra("content")) {
            final String sender = intent.getStringExtra("sender");
            final String content = intent.getStringExtra("content");
            log("接收到任务：转发来自 " + sender + " 的短信。");
            new Thread(() -> sendEmail(sender, content)).start();
        }

        return START_STICKY; // 系统杀死后尝试重启服务
    }

    private void sendEmail(final String smsSender, final String smsContent) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        final String fromEmail = prefs.getString("sender_email", "");
        final String fromPassword = prefs.getString("sender_password", "");
        final String toEmail = prefs.getString("receiver_email", "");

        if (fromEmail.isEmpty() || fromPassword.isEmpty() || toEmail.isEmpty()) {
            log("邮件发送失败：邮箱配置不完整。");
            return;
        }

        try {
            log("正在连接邮件服务器...");
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.qq.com");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.port", "465");

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
            log("邮件发送成功！");

        } catch (MessagingException e) {
            log("邮件发送失败: " + e.getMessage());
            Log.e(TAG, "邮件发送异常详情: ", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 服务销毁时，注销短信接收器
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver);
        }
        log("服务已停止。");
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