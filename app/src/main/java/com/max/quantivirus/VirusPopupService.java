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
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;

public class VirusPopupService extends Service {
    private static final String TAG = "VirusPopupService";
    
    private WindowManager windowManager;
    private View popupView;
    private WindowManager.LayoutParams params;
    
    private String appName;
    private String packageName;
    
    private boolean isShowing = false;
    private BroadcastReceiver systemStateReceiver;
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "VirusPopupService started with intent: " + (intent != null ? "yes" : "no"));
        
        if (intent != null) {
            appName = intent.getStringExtra("appName");
            packageName = intent.getStringExtra("packageName");
            
            if (appName == null || appName.isEmpty()) {
                appName = "Unknown App";
            }
            if (packageName == null || packageName.isEmpty()) {
                packageName = "unknown.package";
            }
            
            Log.d(TAG, "VirusPopupService: appName=" + appName + ", packageName=" + packageName);
            
            showVirusPopup();
        } else {
            Log.e(TAG, "VirusPopupService: intent is null!");
        }
        
        // Возвращаем START_STICKY чтобы сервис перезапускался при убийстве
        return START_STICKY;
    }
    
    private void showVirusPopup() {
        Log.d(TAG, "showVirusPopup called, isShowing=" + isShowing);
        
        if (isShowing) {
            Log.d(TAG, "Popup already showing, returning");
            return;
        }
        
        // Проверяем разрешение на оверлей
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            boolean hasPermission = android.provider.Settings.canDrawOverlays(this);
            Log.d(TAG, "Overlay permission check: " + hasPermission);
            
            if (!hasPermission) {
                Log.e(TAG, "Overlay permission not granted, cannot show popup");
                Toast.makeText(this, "❌ Разрешение на всплывающие окна не предоставлено! Предупреждение о вирусе не может быть показано.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        
        Log.d(TAG, "Starting to show virus popup...");
        
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            
            // Создаем layout для всплывающего окна
            LayoutInflater inflater = LayoutInflater.from(this);
            popupView = inflater.inflate(R.layout.virus_popup_overlay, null);
            
            // Настраиваем параметры окна с максимальной защитой от скрытия
            params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES,
                PixelFormat.TRANSLUCENT
            );
            
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 0;
            
            // Инициализируем UI элементы
            initializeUI();
            
            // Добавляем окно на экран
            windowManager.addView(popupView, params);
            isShowing = true;
            
            Log.d(TAG, "Virus popup shown successfully for: " + packageName);
            Log.d(TAG, "Popup view added to window manager, isShowing=" + isShowing);
            
            // Проверяем, что все элементы найдены
            Log.d(TAG, "UI elements check:");
            Log.d(TAG, "- popupView: " + (popupView != null ? "found" : "null"));
            Log.d(TAG, "- popupView parent: " + (popupView.getParent() != null ? "has parent" : "no parent"));
            Log.d(TAG, "- popupView visibility: " + popupView.getVisibility());
            Log.d(TAG, "- popupView width: " + popupView.getWidth() + ", height: " + popupView.getHeight());
            
            // Блокируем все попытки закрыть окно
            blockWindowClosing();
            
            // Регистрируем receiver для отслеживания изменений системы
            registerSystemStateReceiver();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing virus popup: " + e.getMessage());
        }
    }
    
    private void registerSystemStateReceiver() {
        try {
            systemStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null) {
                        switch (action) {
                            case Intent.ACTION_CONFIGURATION_CHANGED:
                            case Intent.ACTION_SCREEN_OFF:
                            case Intent.ACTION_SCREEN_ON:
                            case Intent.ACTION_USER_PRESENT:
                            case Intent.ACTION_CLOSE_SYSTEM_DIALOGS:
                            case Intent.ACTION_HEADSET_PLUG:
                            case Intent.ACTION_BATTERY_CHANGED:
                                // При изменении конфигурации или состояния экрана проверяем видимость окна
                                Log.d(TAG, "System state changed: " + action + ", checking popup visibility");
                                checkAndRestorePopup();
                                break;
                        }
                    }
                }
            };
            
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            filter.addAction(Intent.ACTION_HEADSET_PLUG);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            
            registerReceiver(systemStateReceiver, filter);
            Log.d(TAG, "System state receiver registered");
            
        } catch (Exception e) {
            Log.e(TAG, "Error registering system state receiver: " + e.getMessage());
        }
    }
    
    private void checkAndRestorePopup() {
        try {
            if (isShowing && popupView != null && isVirusStillPresent()) {
                boolean isWindowVisible = isWindowVisible();
                if (!isWindowVisible) {
                    Log.d(TAG, "Popup not visible, attempting to restore...");
                    if (popupView.getParent() == null) {
                        windowManager.addView(popupView, params);
                        Log.d(TAG, "Popup restored via system state change");
                    } else {
                        windowManager.updateViewLayout(popupView, params);
                        Log.d(TAG, "Popup visibility restored via system state change");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking and restoring popup: " + e.getMessage());
        }
    }
    
    private void initializeUI() {
        try {
            Log.d(TAG, "Initializing UI...");
            
            // Устанавливаем имя приложения
            TextView nameView = popupView.findViewById(R.id.virus_app_name);
            if (nameView != null) {
                nameView.setText(appName);
                Log.d(TAG, "App name set: " + appName);
            } else {
                Log.e(TAG, "virus_app_name TextView not found!");
            }
            
            // Устанавливаем package name
            TextView packageView = popupView.findViewById(R.id.virus_package);
            if (packageView != null) {
                packageView.setText(packageName);
                Log.d(TAG, "Package name set: " + packageName);
            } else {
                Log.e(TAG, "virus_package TextView not found!");
            }
            
            // Устанавливаем иконку приложения
            ImageView iconView = popupView.findViewById(R.id.virus_icon);
            if (iconView != null && !packageName.equals("unknown.package")) {
                try {
                    PackageManager pm = getPackageManager();
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    iconView.setImageDrawable(pm.getApplicationIcon(appInfo));
                    Log.d(TAG, "App icon set successfully");
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "App icon not found, using default");
                    // Иконка не найдена, оставляем дефолтную
                }
            } else if (iconView == null) {
                Log.e(TAG, "virus_icon ImageView not found!");
            }
            
            // Настраиваем кнопку удаления
            Button deleteButton = popupView.findViewById(R.id.virus_delete_button);
            if (deleteButton != null) {
                deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        handleDeleteButtonClick();
                    }
                });
                Log.d(TAG, "Delete button configured successfully");
            } else {
                Log.e(TAG, "virus_delete_button Button not found!");
            }
            
            // Блокируем все touch события кроме кнопки удаления
            popupView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    // Разрешаем только события для кнопки удаления
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        View child = findChildAt(popupView, (int) event.getX(), (int) event.getY());
                        if (child instanceof Button) {
                            return false; // Разрешаем обработку кнопки
                        }
                    }
                    return true; // Блокируем все остальные touch события
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI: " + e.getMessage());
        }
    }
    
    private View findChildAt(View parent, int x, int y) {
        if (parent instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) parent;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    int[] location = new int[2];
                    child.getLocationInWindow(location);
                    if (x >= location[0] && x < location[0] + child.getWidth() &&
                        y >= location[1] && y < location[1] + child.getHeight()) {
                        return child;
                    }
                }
            }
        }
        return null;
    }
    
    private void handleDeleteButtonClick() {
        try {
            // Проверяем package name
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
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                          Intent.FLAG_ACTIVITY_NO_ANIMATION |
                          Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            // Проверяем, что есть приложение для обработки этого интента
            if (intent.resolveActivity(pm) != null) {
                startActivity(intent);
                Toast.makeText(this, "Диалог удаления открыт!", Toast.LENGTH_SHORT).show();
                
                // Закрываем окно вируса через небольшую задержку
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        hideVirusPopup();
                    }
                }, 1000);
                
            } else {
                // Альтернативный способ - открываем настройки приложения
                launchSettingsFallback();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling delete button click: " + e.getMessage());
            launchSettingsFallback();
        }
    }
    
    private void launchSettingsFallback() {
        try {
            Intent settingsIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            settingsIntent.setData(Uri.parse("package:" + packageName));
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            
            startActivity(settingsIntent);
            hideVirusPopup();
            
        } catch (Exception e) {
            Log.e(TAG, "Settings fallback also failed: " + e.getMessage());
            Toast.makeText(this, 
                "Не удалось открыть диалог удаления. Попробуйте удалить приложение вручную.", 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void blockWindowClosing() {
        // Создаем периодическую проверку, чтобы окно не закрылось
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isShowing && popupView != null) {
                    // Проверяем, что вирус все еще существует
                    if (isVirusStillPresent()) {
                        // Проверяем, что окно отображается на экране
                        boolean isWindowVisible = isWindowVisible();
                        
                        if (!isWindowVisible) {
                            try {
                                // Если окно не видимо, восстанавливаем его
                                if (popupView.getParent() == null) {
                                    windowManager.addView(popupView, params);
                                    Log.d(TAG, "Virus popup restored after being removed");
                                } else {
                                    // Если окно есть в window manager, но не видимо, обновляем его
                                    windowManager.updateViewLayout(popupView, params);
                                    Log.d(TAG, "Virus popup visibility restored");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error restoring popup: " + e.getMessage());
                            }
                        }
                        
                        // Дополнительная защита: принудительно обновляем параметры окна
                        try {
                            if (popupView.getParent() != null) {
                                // Обновляем layout для предотвращения скрытия
                                windowManager.updateViewLayout(popupView, params);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating popup layout: " + e.getMessage());
                        }
                        
                        // Продолжаем проверку
                        handler.postDelayed(this, 300); // Еще больше увеличиваем частоту проверки
                    } else {
                        // Вирус удален, закрываем окно
                        hideVirusPopup();
                    }
                }
            }
        }, 300); // Уменьшаем интервал для максимально быстрого восстановления
    }
    
    private boolean isWindowVisible() {
        try {
            if (popupView == null || popupView.getParent() == null) {
                return false;
            }
            
            // Проверяем, что окно действительно видимо на экране
            return popupView.getVisibility() == View.VISIBLE && 
                   popupView.getWindowVisibility() == View.VISIBLE;
        } catch (Exception e) {
            Log.e(TAG, "Error checking window visibility: " + e.getMessage());
            return false;
        }
    }
    
    private boolean isVirusStillPresent() {
        try {
            if (packageName != null && !packageName.equals("unknown.package")) {
                PackageManager pm = getPackageManager();
                pm.getApplicationInfo(packageName, 0);
                return true; // Приложение все еще существует
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Приложение удалено
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking virus presence: " + e.getMessage());
        }
        return false;
    }
    
    private void hideVirusPopup() {
        if (!isShowing || popupView == null) {
            return;
        }
        
        try {
            if (popupView.getParent() != null) {
                windowManager.removeView(popupView);
            }
            isShowing = false;
            Log.d(TAG, "Virus popup hidden");
        } catch (Exception e) {
            Log.e(TAG, "Error hiding virus popup: " + e.getMessage());
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Отменяем регистрацию receiver
        if (systemStateReceiver != null) {
            try {
                unregisterReceiver(systemStateReceiver);
                systemStateReceiver = null;
                Log.d(TAG, "System state receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering system state receiver: " + e.getMessage());
            }
        }
        
        hideVirusPopup();
        Log.d(TAG, "VirusPopupService destroyed");
    }
} 