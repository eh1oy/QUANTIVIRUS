package com.max.quantivirus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.app.ActivityManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AutonomyService extends Service {
    private static final String CHANNEL_ID = "autonomy_channel";
    private static final int NOTIF_ID = 2;
    private Handler autonomyHandler;
    private static final long AUTONOMY_CHECK_INTERVAL = 15000; // 15 секунд
    private boolean isDestroyed = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            startForeground(NOTIF_ID, getNotification());
            startAutonomyMonitoring();
            android.util.Log.d("AutonomyService", "Service created successfully");
        } catch (Exception e) {
            android.util.Log.e("AutonomyService", "Error in onCreate: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            android.util.Log.d("AutonomyService", "Service started with startId: " + startId);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIF_ID, getNotification());
            }
            
        } catch (Exception e) {
            android.util.Log.e("AutonomyService", "Error in onStartCommand: " + e.getMessage());
        }
        return START_STICKY;
    }

    private void startAutonomyMonitoring() {
        autonomyHandler = new Handler(Looper.getMainLooper());
        autonomyHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isDestroyed) return;
                    
                    // Проверяем, работает ли основной антивирусный сервис
                    if (!isAntivirusServiceRunning()) {
                        android.util.Log.w("AutonomyService", "Antivirus service not running, restarting");
                        restartAntivirusService();
                    }
                    
                    // Проверяем, работает ли сервис всплывающих окон
                    if (!isVirusPopupServiceRunning()) {
                        android.util.Log.w("AutonomyService", "VirusPopupService not running, restarting");
                        restartVirusPopupService();
                    }
                    
                    // Продолжаем мониторинг
                    if (!isDestroyed) {
                        autonomyHandler.postDelayed(this, AUTONOMY_CHECK_INTERVAL);
                    }
                    
                } catch (Exception e) {
                    android.util.Log.e("AutonomyService", "Error in autonomy monitoring: " + e.getMessage());
                    // Продолжаем мониторинг даже при ошибке
                    if (!isDestroyed) {
                        autonomyHandler.postDelayed(this, AUTONOMY_CHECK_INTERVAL);
                    }
                }
            }
        }, AUTONOMY_CHECK_INTERVAL);
    }

    private boolean isAntivirusServiceRunning() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
                if (services != null) {
                    for (ActivityManager.RunningServiceInfo service : services) {
                        if (service.service.getClassName().equals(AntivirusService.class.getName())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AutonomyService", "Error checking antivirus service: " + e.getMessage());
        }
        return false;
    }

    private boolean isVirusPopupServiceRunning() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
                if (services != null) {
                    for (ActivityManager.RunningServiceInfo service : services) {
                        if (service.service.getClassName().equals(VirusPopupService.class.getName())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AutonomyService", "Error checking virus popup service: " + e.getMessage());
        }
        return false;
    }

    private void restartAntivirusService() {
        try {
            Intent serviceIntent = new Intent(this, AntivirusService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            android.util.Log.d("AutonomyService", "Antivirus service restarted");
        } catch (Exception e) {
            android.util.Log.e("AutonomyService", "Error restarting antivirus service: " + e.getMessage());
        }
    }

    private void restartVirusPopupService() {
        try {
            Intent serviceIntent = new Intent(this, VirusPopupService.class);
            startService(serviceIntent);
            android.util.Log.d("AutonomyService", "VirusPopupService restarted");
        } catch (Exception e) {
            android.util.Log.e("AutonomyService", "Error restarting VirusPopupService: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Автономность QUANTIVIRUS",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Обеспечивает автономную работу антивируса");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ QUANTIVIRUS Автономность")
            .setContentText("Обеспечивает стабильную работу антивируса")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build();
    }

    @Override
    public void onDestroy() {
        try {
            super.onDestroy();
            
            isDestroyed = true;
            
            // Останавливаем мониторинг
            if (autonomyHandler != null) {
                autonomyHandler.removeCallbacksAndMessages(null);
            }
            
            android.util.Log.d("AutonomyService", "Service destroyed");
        } catch (Exception e) {
            android.util.Log.e("AutonomyService", "Error in onDestroy: " + e.getMessage());
        }
    }
} 