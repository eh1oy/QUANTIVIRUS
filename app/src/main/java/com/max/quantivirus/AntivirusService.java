package com.max.quantivirus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.List;
import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;

public class AntivirusService extends Service {
    private static final String CHANNEL_ID = "quantivirus_channel";
    private static final int NOTIF_ID = 1;
    private static final String DANGEROUS_PACKAGE = "ru.oneme.app";
    private boolean isRunning = false;
    private Thread scanThread;
    private boolean virusDetected = false;
    private long lastVirusWindowTime = 0;
    private static final long VIRUS_WINDOW_INTERVAL = 1000; // 1 секунда между показами окна
    private Handler selfRestartHandler;
    private static final long RESTART_CHECK_INTERVAL = 1000; // 1 секунда
    private boolean isDestroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Устанавливаем глобальный обработчик исключений
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable ex) {
                    android.util.Log.e("AntivirusService", "Uncaught exception: " + ex.getMessage());
                    ex.printStackTrace();
                    
                    // Перезапускаем сервис при критической ошибке
                    try {
                        if (!isDestroyed) {
                            restartSelf();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("AntivirusService", "Error restarting service after crash: " + e.getMessage());
                    }
                }
            });
            
            createNotificationChannel();
            startForeground(NOTIF_ID, getNotification());
            startContinuousScanning();
            startSelfRestartMonitoring();
            android.util.Log.d("AntivirusService", "Service created successfully");
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error in onCreate: " + e.getMessage());
        }
    }
    
    private void startSelfRestartMonitoring() {
        selfRestartHandler = new Handler(Looper.getMainLooper());
        selfRestartHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isDestroyed) {
                        return; // Не выполняем мониторинг если сервис уничтожен
                    }
                    
                    // Проверяем, что сервис все еще работает в системе
                    if (!isServiceRunningInSystem()) {
                        android.util.Log.w("AntivirusService", "Service not running in system, restarting self");
                        restartSelf();
                    }
                    
                    // Продолжаем мониторинг только если сервис не уничтожен
                    if (!isDestroyed) {
                        selfRestartHandler.postDelayed(this, RESTART_CHECK_INTERVAL);
                    }
                } catch (Exception e) {
                    android.util.Log.e("AntivirusService", "Error in self restart monitoring: " + e.getMessage());
                    // Продолжаем мониторинг даже при ошибке, но только если сервис не уничтожен
                    if (!isDestroyed) {
                        selfRestartHandler.postDelayed(this, RESTART_CHECK_INTERVAL);
                    }
                }
            }
        }, RESTART_CHECK_INTERVAL);
    }
    
    private void startAutonomyMonitoring() {
        // Создаем отдельный поток для мониторинга автономности
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.util.Log.d("AntivirusService", "Autonomy monitoring started");
                
                while (!isDestroyed) {
                    try {
                        // Проверяем каждые 30 секунд
                        Thread.sleep(30000);
                        
                        if (isDestroyed) break;
                        
                        // Проверяем, что сервис все еще работает
                        if (!isServiceRunningInSystem()) {
                            android.util.Log.w("AntivirusService", "Service not running, attempting restart");
                            restartSelf();
                            break;
                        }
                        
                        // Проверяем, что уведомление активно
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForeground(NOTIF_ID, getNotification());
                        }
                        
                        // Проверяем, что сканирование активно
                        if (!isRunning) {
                            android.util.Log.w("AntivirusService", "Scanning stopped, restarting");
                            startContinuousScanning();
                        }
                        
                    } catch (InterruptedException e) {
                        android.util.Log.d("AntivirusService", "Autonomy monitoring interrupted");
                        break;
                    } catch (Exception e) {
                        android.util.Log.e("AntivirusService", "Error in autonomy monitoring: " + e.getMessage());
                    }
                }
                
                android.util.Log.d("AntivirusService", "Autonomy monitoring stopped");
            }
        }).start();
    }
    
    private boolean isServiceRunningInSystem() {
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
            android.util.Log.e("AntivirusService", "Error checking if service is running in system: " + e.getMessage());
        }
        return false;
    }
    
    private void restartSelf() {
        try {
            android.util.Log.d("AntivirusService", "Restarting service");
            Intent restartIntent = new Intent(this, AntivirusService.class);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(restartIntent);
            } else {
                startService(restartIntent);
            }
            
            // Останавливаем текущий сервис
            stopSelf();
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error restarting service: " + e.getMessage());
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            android.util.Log.d("AntivirusService", "Service started with startId: " + startId);
            
            if (!isRunning) {
                startContinuousScanning();
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForeground(NOTIF_ID, getNotification());
            }
            
            // Запускаем дополнительный мониторинг для автономности
            startAutonomyMonitoring();
            
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error in onStartCommand: " + e.getMessage());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            super.onDestroy();
            
            isDestroyed = true;
            stopContinuousScanning();
            
            // Останавливаем мониторинг самовосстановления
            if (selfRestartHandler != null) {
                selfRestartHandler.removeCallbacksAndMessages(null);
            }
            
            android.util.Log.d("AntivirusService", "Service destroyed");
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error in onDestroy: " + e.getMessage());
        }
    }

    private void startContinuousScanning() {
        if (isRunning) {
            return;
        }
        
        try {
            isRunning = true;
            scanThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    android.util.Log.d("AntivirusService", "Scan thread started");
                    while (isRunning && !isDestroyed) {
                        try {
                            checkForVirus();
                            Thread.sleep(1000); // Проверяем каждую секунду
                        } catch (InterruptedException e) {
                            android.util.Log.d("AntivirusService", "Scan thread interrupted");
                            break;
                        } catch (Exception e) {
                            android.util.Log.e("AntivirusService", "Error in scan loop: " + e.getMessage());
                            try {
                                Thread.sleep(2000); // Уменьшаем интервал при ошибке
                            } catch (InterruptedException ie) {
                                break;
                            }
                        }
                    }
                    android.util.Log.d("AntivirusService", "Scan thread stopped");
                }
            });
            scanThread.start();
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error starting continuous scanning: " + e.getMessage());
            isRunning = false;
        }
    }

    private void stopContinuousScanning() {
        try {
            isRunning = false;
            if (scanThread != null) {
                scanThread.interrupt();
                scanThread = null;
            }
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error stopping continuous scanning: " + e.getMessage());
        }
    }

    private void checkForVirus() {
        try {
            if (isDestroyed) {
                return; // Не выполняем проверку если сервис уничтожен
            }
            
            PackageManager pm = getPackageManager();
            if (pm == null) {
                return;
            }
            
            // Проверяем конкретное приложение
            boolean virusFound = checkSpecificApp(pm);
            
            if (!virusFound) {
                // Проверяем все приложения
                try {
                    List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                    if (apps == null) {
                        return;
                    }
                    
                    for (ApplicationInfo app : apps) {
                        if (app == null || app.packageName == null || isDestroyed) {
                            continue;
                        }
                        
                        if (DANGEROUS_PACKAGE.equals(app.packageName)) {
                            virusDetected = true;
                            checkAndShowVirusWindow(app, pm);
                            return;
                        }
                    }
                    
                    // Если вирус не найден, сбрасываем флаг
                    if (virusDetected) {
                        virusDetected = false;
                        lastVirusWindowTime = 0;
                    }
                    
                } catch (Exception e) {
                    android.util.Log.e("AntivirusService", "Error scanning apps: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error in checkForVirus: " + e.getMessage());
        }
    }
    
    private boolean checkSpecificApp(PackageManager pm) {
        try {
            android.util.Log.d("AntivirusService", "checkSpecificApp: checking for " + DANGEROUS_PACKAGE);
            
            ApplicationInfo appInfo = pm.getApplicationInfo(DANGEROUS_PACKAGE, 0);
            if (appInfo != null) {
                android.util.Log.d("AntivirusService", "checkSpecificApp: VIRUS FOUND! - " + DANGEROUS_PACKAGE);
                android.util.Log.d("AntivirusService", "checkSpecificApp: app label = " + pm.getApplicationLabel(appInfo));
                virusDetected = true;
                checkAndShowVirusWindow(appInfo, pm);
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            android.util.Log.d("AntivirusService", "checkSpecificApp: virus not found - " + DANGEROUS_PACKAGE);
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error checking specific app: " + e.getMessage());
        }
        return false;
    }

    private void checkAndShowVirusWindow(ApplicationInfo app, PackageManager pm) {
        try {
            if (app == null || pm == null || isDestroyed) {
                android.util.Log.d("AntivirusService", "checkAndShowVirusWindow: null parameters or destroyed");
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            
            if (currentTime - lastVirusWindowTime < VIRUS_WINDOW_INTERVAL) {
                android.util.Log.d("AntivirusService", "checkAndShowVirusWindow: too soon to show window");
                return; // Пропускаем, если не прошло достаточно времени
            }
            
            // Проверяем, не запущен ли уже VirusPopupService
            if (isVirusDetectedActivityRunning()) {
                android.util.Log.d("AntivirusService", "checkAndShowVirusWindow: popup already running");
                return; // Пропускаем, если всплывающее окно уже открыто
            }
            
            // Дополнительная проверка - убеждаемся, что вирус все еще существует
            try {
                pm.getApplicationInfo(app.packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                android.util.Log.d("AntivirusService", "checkAndShowVirusWindow: virus app not found anymore");
                virusDetected = false;
                return;
            }
            
            android.util.Log.d("AntivirusService", "checkAndShowVirusWindow: launching popup for " + app.packageName);
            lastVirusWindowTime = currentTime;
            launchVirusDetectedActivity(app, pm);
            
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error in checkAndShowVirusWindow: " + e.getMessage());
        }
    }
    
    private boolean isVirusDetectedActivityRunning() {
        try {
            // Проверяем, что VirusPopupService действительно работает и показывает окно
            // Простое наличие сервиса не означает, что окно показано
            return false; // Всегда разрешаем запуск нового окна
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error checking running popup service: " + e.getMessage());
        }
        return false;
    }
    
    private void launchVirusDetectedActivity(ApplicationInfo app, PackageManager pm) {
        try {
            if (app == null || pm == null || isDestroyed) {
                android.util.Log.d("AntivirusService", "launchVirusDetectedActivity: null parameters or destroyed");
                return;
            }
            
            // Проверяем разрешение на оверлей
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    android.util.Log.w("AntivirusService", "Overlay permission not granted, cannot show virus popup");
                    return;
                }
            }
            
            // Получаем имя приложения
            String appName = "Unknown App";
            try {
                appName = pm.getApplicationLabel(app).toString();
            } catch (Exception e) {
                android.util.Log.e("AntivirusService", "Error getting app label: " + e.getMessage());
            }
            
            android.util.Log.d("AntivirusService", "launchVirusDetectedActivity: launching popup for " + app.packageName + " (" + appName + ")");
            
            // Создаем интент для запуска всплывающего окна вируса
            Intent intent = new Intent(AntivirusService.this, VirusPopupService.class);
            intent.putExtra("appName", appName);
            intent.putExtra("packageName", app.packageName);
            
            startService(intent);
            android.util.Log.d("AntivirusService", "launchVirusDetectedActivity: popup service started successfully");
            
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error launching VirusPopupService: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "QUANTIVIRUS Background",
                        NotificationManager.IMPORTANCE_LOW
                );
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error creating notification channel: " + e.getMessage());
        }
    }

    private Notification getNotification() {
        try {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("QUANTIVIRUS работает")
                    .setContentText("Антивирус защищает устройство")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentIntent(pendingIntent);
            
            return builder.build();
        } catch (Exception e) {
            android.util.Log.e("AntivirusService", "Error creating notification: " + e.getMessage());
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("QUANTIVIRUS")
                    .setContentText("Работает")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .build();
        }
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 