package com.max.quantivirus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.drawable.Drawable;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class VirusPopupWindow {
    private Context context;
    private WindowManager windowManager;
    private View popupView;
    private String packageName;
    private String appName;
    private Drawable icon;
    
    public VirusPopupWindow(Context context, String packageName, String appName, Drawable icon) {
        this.context = context;
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        createPopupWindow();
    }
    
    private void createPopupWindow() {
        // Создаем layout для всплывающего окна
        LayoutInflater inflater = LayoutInflater.from(context);
        popupView = inflater.inflate(R.layout.popup_virus_detected, null);
        
        // Настраиваем элементы интерфейса
        TextView nameView = popupView.findViewById(R.id.virus_app_name);
        TextView packageView = popupView.findViewById(R.id.virus_package);
        ImageView iconView = popupView.findViewById(R.id.virus_icon);
        Button deleteButton = popupView.findViewById(R.id.virus_delete_button);
        
        nameView.setText(appName);
        packageView.setText(packageName);
        if (icon != null) iconView.setImageDrawable(icon);
        
        // Настраиваем кнопку удаления
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    android.util.Log.d("VirusPopupWindow", "Delete button clicked for package: " + packageName);
                    
                    // Создаем интент для удаления приложения
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + packageName));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    
                    // Запускаем активность удаления
                    context.startActivity(intent);
                    
                    // Показываем уведомление об успешном удалении
                    android.widget.Toast.makeText(context, 
                        "Приложение удалено!", android.widget.Toast.LENGTH_SHORT).show();
                    
                    // Закрываем всплывающее окно после удаления
                    removePopup();
                    
                } catch (Exception e) {
                    android.util.Log.e("VirusPopupWindow", "Error deleting app: " + e.getMessage());
                    e.printStackTrace();
                    
                    // Показываем ошибку пользователю
                    android.widget.Toast.makeText(context, 
                        "Ошибка при удалении приложения", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        // Создаем параметры окна для отображения поверх всего
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;
        
        // Добавляем окно в WindowManager
        try {
            android.util.Log.d("VirusPopupWindow", "Attempting to add popup window...");
            windowManager.addView(popupView, params);
            android.util.Log.d("VirusPopupWindow", "Popup window added successfully");
        } catch (SecurityException e) {
            android.util.Log.e("VirusPopupWindow", "SecurityException: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            android.util.Log.e("VirusPopupWindow", "IllegalArgumentException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            android.util.Log.e("VirusPopupWindow", "Error adding popup window: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private int getWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
    }
    
    public void removePopup() {
        if (popupView != null && windowManager != null) {
            try {
                windowManager.removeView(popupView);
                android.util.Log.d("VirusPopupWindow", "Popup window removed successfully");
            } catch (Exception e) {
                android.util.Log.e("VirusPopupWindow", "Error removing popup window: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    public boolean isShowing() {
        return popupView != null && popupView.getParent() != null;
    }
} 