package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                try {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    if (pdus == null) {
                        return;
                    }

                    StringBuilder smsContent = new StringBuilder();
                    String sender = "";

                    for (int i = 0; i < pdus.length; i++) {
                        String format = bundle.getString("format");
                        SmsMessage message = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                        smsContent.append(message.getMessageBody());
                        if (sender.isEmpty()) {
                            sender = message.getOriginatingAddress();
                        }
                    }

                    log(context, "成功接收到来自 " + sender + " 的短信。");

                    // 将短信内容传递给正在运行的 EmailService 去处理
                    Intent serviceIntent = new Intent(context, EmailService.class);
                    serviceIntent.putExtra("sender", sender);
                    serviceIntent.putExtra("content", smsContent.toString());
                    context.startService(serviceIntent); // 使用 startService 而不是 startForegroundService

                } catch (Exception e) {
                    log(context, "解析短信失败: " + e.getMessage());
                    Log.e(TAG, "解析短信异常详情", e);
                }
            }
        }
    }

    // 辅助方法，用于发送日志广播和打印Logcat
    private void log(Context context, String message) {
        Log.d(TAG, message);
        Intent intent = new Intent(EmailService.ACTION_UPDATE_LOG);
        intent.putExtra(EmailService.EXTRA_LOG_MESSAGE, message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}