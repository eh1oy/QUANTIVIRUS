package com.max.quantivirus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        android.util.Log.d("BootReceiver", "Received action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "android.intent.action.MY_PACKAGE_REPLACED".equals(action) ||
            "android.intent.action.PACKAGE_REPLACED".equals(action)) {
            
            android.util.Log.d("BootReceiver", "Boot completed, starting antivirus service");
            
            // Проверяем, включен ли антивирус
            android.content.SharedPreferences prefs = context.getSharedPreferences("quantivirus_prefs", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("antivirus_enabled", true);
            
            if (enabled) {
                // Запускаем сервис с задержкой для стабильности
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            android.util.Log.d("BootReceiver", "Starting all services after delay");
                            
                            // Запускаем основной антивирусный сервис
                            Intent antivirusIntent = new Intent(context, AntivirusService.class);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(antivirusIntent);
                            } else {
                                context.startService(antivirusIntent);
                            }
                            
                            // Запускаем сервис автономности
                            Intent autonomyIntent = new Intent(context, AutonomyService.class);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(autonomyIntent);
                            } else {
                                context.startService(autonomyIntent);
                            }
                            
                            android.util.Log.d("BootReceiver", "All services started successfully");
                            
                        } catch (Exception e) {
                            android.util.Log.e("BootReceiver", "Error starting services: " + e.getMessage());
                        }
                    }
                }, 10000); // 10 секунд задержки
            }
        }
    }
} 