package org.mannerth.starxcar.driver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

/** Keeps the driver process eligible for location callbacks while online. */
public final class DriverForegroundService extends Service {
    public static final String ACTION_START =
            "org.mannerth.starxcar.driver.action.START_FOREGROUND";
    public static final String ACTION_STOP =
            "org.mannerth.starxcar.driver.action.STOP_FOREGROUND";

    private static final String CHANNEL_ID = "starx_driver_work";
    private static final int NOTIFICATION_ID = 2401;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(org.mannerth.starxcar.driver.R.mipmap.ic_launcher)
                .setContentTitle("StarX-Car 司机端")
                .setContentText("正在听单并上报位置")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "司机听单与定位",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("司机上线时保持位置服务运行");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
