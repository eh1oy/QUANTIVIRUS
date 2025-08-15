package com.max.quantivirus;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

public class VirusOverlayService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private String appName = "Unknown App";
    private String packageName = "unknown.package";
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.d("VirusOverlayService", "onStartCommand called with startId: " + startId);
        
        if (intent != null) {
            appName = intent.getStringExtra("appName");
            packageName = intent.getStringExtra("packageName");
            
            if (appName == null || appName.isEmpty()) {
                appName = "Unknown App";
            }
            if (packageName == null || packageName.isEmpty()) {
                packageName = "unknown.package";
            }
            
            android.util.Log.d("VirusOverlayService", "Received appName: " + appName + ", packageName: " + packageName);
        }
        
        showOverlay();
        return START_STICKY;
    }
    
    private void showOverlay() {
        android.util.Log.d("VirusOverlayService", "showOverlay: starting overlay creation");
        
        // Проверяем разрешение на оверлей
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.util.Log.e("VirusOverlayService", "Overlay permission not granted!");
                Toast.makeText(this, "❌ Разрешение на оверлей НЕ предоставлено! Оверлей не может быть отображен.", Toast.LENGTH_LONG).show();
                
                // Показываем инструкцию пользователю
                showPermissionInstructions();
                return;
            }
        }
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Создаем параметры окна для полноэкранного оверлея
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.START;
        
        android.util.Log.d("VirusOverlayService", "showOverlay: created window params");
        
        // Создаем view для оверлея
        overlayView = LayoutInflater.from(this).inflate(R.layout.virus_overlay, null);
        
        android.util.Log.d("VirusOverlayService", "showOverlay: inflated overlay view");
        
        // Настраиваем UI элементы
        setupUI();
        
        // Добавляем view в window manager
        try {
            windowManager.addView(overlayView, params);
            android.util.Log.d("VirusOverlayService", "showOverlay: overlay view added successfully");
            Toast.makeText(this, "✅ Оверлей вируса отображен!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("VirusOverlayService", "Error adding overlay view: " + e.getMessage());
            Toast.makeText(this, "❌ Ошибка отображения оверлея: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showPermissionInstructions() {
        try {
            Toast.makeText(this, "📋 Инструкция по настройке разрешений:", Toast.LENGTH_LONG).show();
            
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VirusOverlayService.this, "1. Откройте Настройки -> Приложения", Toast.LENGTH_LONG).show();
                }
            }, 2000);
            
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VirusOverlayService.this, "2. Найдите QUANTIVIRUS -> Разрешения", Toast.LENGTH_LONG).show();
                }
            }, 4000);
            
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VirusOverlayService.this, "3. Включите 'Отображение поверх других приложений'", Toast.LENGTH_LONG).show();
                }
            }, 6000);
            
        } catch (Exception e) {
            android.util.Log.e("VirusOverlayService", "Error showing permission instructions: " + e.getMessage());
        }
    }
    
    private void setupUI() {
        // Настраиваем тексты
        TextView nameView = overlayView.findViewById(R.id.overlay_app_name);
        TextView packageView = overlayView.findViewById(R.id.overlay_package);
        ImageView iconView = overlayView.findViewById(R.id.overlay_icon);
        Button deleteButton = overlayView.findViewById(R.id.overlay_delete_button);
        
        if (nameView != null) {
            nameView.setText(appName);
        }
        
        if (packageView != null) {
            packageView.setText(packageName);
        }
        
        // Устанавливаем иконку приложения
        try {
            PackageManager pm = getPackageManager();
            if (pm != null && iconView != null && !packageName.equals("unknown.package")) {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                iconView.setImageDrawable(pm.getApplicationIcon(appInfo));
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Иконка не найдена
        } catch (Exception e) {
            android.util.Log.e("VirusOverlayService", "Error setting app icon: " + e.getMessage());
        }
        
        // Настраиваем кнопку удаления
        if (deleteButton != null) {
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchUninstallDialog();
                }
            });
        }
        
        // Блокируем все touch события на оверлее
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Блокируем все touch события, кроме кнопки удаления
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = event.getX();
                    float y = event.getY();
                    
                    // Проверяем, не нажата ли кнопка удаления
                    if (deleteButton != null) {
                        int[] location = new int[2];
                        deleteButton.getLocationOnScreen(location);
                        
                        if (x >= location[0] && x <= location[0] + deleteButton.getWidth() &&
                            y >= location[1] && y <= location[1] + deleteButton.getHeight()) {
                            // Разрешаем нажатие на кнопку
                            return false;
                        }
                    }
                    
                    // Блокируем все остальные нажатия
                    return true;
                }
                return true;
            }
        });
    }
    
    private void launchUninstallDialog() {
        try {
            if (packageName == null || packageName.isEmpty() || packageName.equals("unknown.package")) {
                Toast.makeText(this, "Ошибка: имя пакета не найдено", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Проверяем, что приложение установлено
            PackageManager pm = getPackageManager();
            try {
                pm.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Toast.makeText(this, "Приложение не найдено на устройстве", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Toast.makeText(this, "Запуск удаления приложения...", Toast.LENGTH_SHORT).show();
            
            // Создаем интент для удаления
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (intent.resolveActivity(pm) != null) {
                startActivity(intent);
                Toast.makeText(this, "Диалог удаления открыт!", Toast.LENGTH_SHORT).show();
                
                // Закрываем оверлей через задержку
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopSelf();
                    }
                }, 2000);
                
            } else {
                launchSettingsFallback();
            }
            
        } catch (Exception e) {
            android.util.Log.e("VirusOverlayService", "Error launching uninstall dialog: " + e.getMessage());
            launchSettingsFallback();
        }
    }
    
    private void launchSettingsFallback() {
        try {
            Intent settingsIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            settingsIntent.setData(Uri.parse("package:" + packageName));
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            startActivity(settingsIntent);
            stopSelf();
            
        } catch (Exception e) {
            android.util.Log.e("VirusOverlayService", "Settings fallback also failed: " + e.getMessage());
            Toast.makeText(this, 
                "Не удалось открыть диалог удаления. Попробуйте удалить приложение вручную.", 
                Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                android.util.Log.e("VirusOverlayService", "Error removing overlay view: " + e.getMessage());
            }
        }
    }
} 