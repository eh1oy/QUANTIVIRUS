package com.max.quantivirus;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.SharedPreferences;
import android.content.Context;
import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.app.AlertDialog;
import android.content.DialogInterface;
import java.util.List;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {
    private SwitchMaterial antivirusSwitch;
    private MaterialButton githubButton, siteButton, telegramButton;
    private FloatingActionButton fabScan;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "quantivirus_prefs";
    private static final String KEY_ANTIVIRUS_ENABLED = "antivirus_enabled";
    private Handler serviceMonitor;
    private static final long MONITOR_INTERVAL = 1000; // 1 секунда
    private boolean isDestroyed = false;
    
    // Константы для разрешений
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1234;
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 5678;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // Устанавливаем глобальный обработчик исключений
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable ex) {
                    android.util.Log.e("MainActivity", "Uncaught exception: " + ex.getMessage());
                    ex.printStackTrace();
                    
                    // Перезапускаем активность при критической ошибке
                    try {
                        if (!isDestroyed) {
                            Intent restartIntent = new Intent(MainActivity.this, MainActivity.class);
                            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(restartIntent);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error restarting activity: " + e.getMessage());
                    }
                }
            });
            
            setContentView(R.layout.activity_main);
            
            // Инициализируем SharedPreferences
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean enabled = prefs.getBoolean(KEY_ANTIVIRUS_ENABLED, true);

            // Находим UI элементы
            antivirusSwitch = findViewById(R.id.antivirus_switch);
            githubButton = findViewById(R.id.github_button);
            siteButton = findViewById(R.id.site_button);
            telegramButton = findViewById(R.id.telegram_button);
            fabScan = findViewById(R.id.fab_scan);

            // Проверяем, что все элементы найдены
            if (antivirusSwitch == null) {
                Toast.makeText(this, "Ошибка инициализации UI", Toast.LENGTH_SHORT).show();
                return;
            }

            // Настраиваем переключатель - ВСЕГДА ВКЛЮЧЕН
            antivirusSwitch.setChecked(true);
            antivirusSwitch.setEnabled(false); // Делаем неактивным
            
            // Проверяем и запрашиваем необходимые разрешения
            checkAndRequestPermissions();
            
            // Антивирус ВСЕГДА запущен
            startAntivirus();
            
            // Запускаем сервис автономности
            startAutonomyService();
            
            // Запускаем мониторинг сервиса для автономности
            startServiceMonitoring();

            antivirusSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    try {
                        // Фейковый переключатель - всегда возвращаем в положение ВКЛ
                        antivirusSwitch.setChecked(true);
                        prefs.edit().putBoolean(KEY_ANTIVIRUS_ENABLED, true).apply();
                        
                        // Показываем сообщение о том, что антивирус всегда активен
                        Toast.makeText(MainActivity.this, "🛡️ QUANTIVIRUS всегда активен и защищает ваше устройство!", Toast.LENGTH_LONG).show();
                        if (isChecked) {
                            startAntivirus();
                            Toast.makeText(MainActivity.this, "Антивирус включен", Toast.LENGTH_SHORT).show();
                        } else {
                            stopAntivirus();
                            Toast.makeText(MainActivity.this, "Антивирус выключен", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error in switch listener: " + e.getMessage());
                    }
                }
            });

            // Настраиваем кнопки
            if (githubButton != null) {
                githubButton.setOnClickListener(v -> {
                    try {
                        openUrl("https://github.com/eh1oy");
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error opening GitHub: " + e.getMessage());
                    }
                });
            }

            if (siteButton != null) {
                siteButton.setOnClickListener(v -> {
                    try {
                        openUrl("https://quantivirus.rf.gd");
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error opening site: " + e.getMessage());
                    }
                });
            }

            if (telegramButton != null) {
                telegramButton.setOnClickListener(v -> {
                    try {
                        openUrl("https://t.me/quantivirus");
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error opening Telegram: " + e.getMessage());
                    }
                });
            }

            // Настраиваем FAB
            if (fabScan != null) {
                fabScan.setOnClickListener(v -> {
                    try {
                        startAntivirus();
                        Toast.makeText(this, "Запущено сканирование", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error starting scan: " + e.getMessage());
                    }
                });
            }

            // Запрашиваем разрешения
            requestPermissions();
            
            // Проверяем статус разрешений и показываем пользователю
            checkPermissionStatus();
            
            // Запускаем мониторинг сервиса
            startServiceMonitoring();

        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error in onCreate: " + e.getMessage());
            Toast.makeText(this, "Ошибка запуска приложения", Toast.LENGTH_LONG).show();
        }
    }
    
    private void startServiceMonitoring() {
        serviceMonitor = new Handler(Looper.getMainLooper());
        serviceMonitor.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isDestroyed) {
                        return; // Не выполняем мониторинг если активность уничтожена
                    }
                    
                    // Проверяем, работает ли сервис
                    if (!isServiceRunning()) {
                        android.util.Log.w("MainActivity", "Service not running, restarting...");
                        startAntivirus();
                    }
                    
                    // Проверяем, включен ли антивирус
                    boolean enabled = prefs.getBoolean(KEY_ANTIVIRUS_ENABLED, true);
                    if (enabled && !isServiceRunning()) {
                        android.util.Log.d("MainActivity", "Antivirus enabled but service not running, restarting");
                        startAntivirus();
                    }
                    
                    // Продолжаем мониторинг только если активность не уничтожена
                    if (!isDestroyed) {
                        serviceMonitor.postDelayed(this, MONITOR_INTERVAL);
                    }
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error in service monitoring: " + e.getMessage());
                    // Продолжаем мониторинг даже при ошибке, но только если активность не уничтожена
                    if (!isDestroyed) {
                        serviceMonitor.postDelayed(this, MONITOR_INTERVAL);
                    }
                }
            }
        }, MONITOR_INTERVAL);
    }
    
    private boolean isServiceRunning() {
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
            android.util.Log.e("MainActivity", "Error checking if service is running: " + e.getMessage());
        }
        return false;
    }

    private void startAntivirus() {
        try {
            Intent serviceIntent = new Intent(this, AntivirusService.class);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            // Дополнительная проверка через 500ms
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!isServiceRunning() && !isDestroyed) {
                            android.util.Log.w("MainActivity", "Service not started, retrying...");
                            Intent retryIntent = new Intent(MainActivity.this, AntivirusService.class);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                startForegroundService(retryIntent);
                            } else {
                                startService(retryIntent);
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error in retry: " + e.getMessage());
                    }
                }
            }, 500); // 500ms
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error starting antivirus service: " + e.getMessage());
            Toast.makeText(this, "Ошибка запуска антивируса", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startAutonomyService() {
        try {
            android.util.Log.d("MainActivity", "Starting autonomy service");
            
            Intent serviceIntent = new Intent(this, AutonomyService.class);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            android.util.Log.d("MainActivity", "Autonomy service started");
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error starting autonomy service: " + e.getMessage());
        }
    }
    


    private void stopAntivirus() {
        try {
            Intent serviceIntent = new Intent(this, AntivirusService.class);
            stopService(serviceIntent);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error stopping antivirus service: " + e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error opening URL: " + e.getMessage());
            Toast.makeText(this, "Ошибка открытия ссылки", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void requestPermissions() {
        try {
            android.util.Log.d("MainActivity", "requestPermissions: starting permission requests");
            
            // Запрашиваем разрешение на отображение поверх других приложений
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    android.util.Log.d("MainActivity", "requestPermissions: requesting overlay permission");
                    Toast.makeText(this, "Требуется разрешение на отображение поверх других приложений", Toast.LENGTH_LONG).show();
                    
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, 1234);
                } else {
                    android.util.Log.d("MainActivity", "requestPermissions: overlay permission already granted");
                }
            }
            
            // Запрашиваем игнорирование оптимизации батареи
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                    android.util.Log.d("MainActivity", "requestPermissions: requesting battery optimization ignore");
                    Toast.makeText(this, "Требуется разрешение на игнорирование оптимизации батареи", Toast.LENGTH_LONG).show();
                    
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                } else {
                    android.util.Log.d("MainActivity", "requestPermissions: battery optimization already ignored");
                }
            }
            
            // Проверяем и запрашиваем другие важные разрешения
            checkAndRequestAdditionalPermissions();
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error requesting permissions: " + e.getMessage());
        }
    }
    
    private void checkAndRequestAdditionalPermissions() {
        try {
            // Проверяем разрешение на запрос всех пакетов (для Android 11+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    android.util.Log.d("MainActivity", "requestPermissions: requesting package install permission");
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    startActivity(intent);
                }
            }
            
            // Проверяем разрешение на автозапуск (для некоторых производителей)
            checkAutoStartPermission();
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error checking additional permissions: " + e.getMessage());
        }
    }
    
    private void checkAutoStartPermission() {
        try {
            // Проверяем различные производители для автозапуска
            String[] autoStartPackages = {
                "com.miui.securitycenter",
                "com.letv.android.letvsafe",
                "com.huawei.systemmanager",
                "com.coloros.safecenter",
                "com.oppo.safe",
                "com.oneplus.security",
                "com.vivo.permissionmanager"
            };
            
            for (String packageName : autoStartPackages) {
                try {
                    getPackageManager().getPackageInfo(packageName, 0);
                    android.util.Log.d("MainActivity", "Found auto-start manager: " + packageName);
                    // Можно добавить уведомление пользователю о необходимости настройки автозапуска
                } catch (PackageManager.NameNotFoundException e) {
                    // Пакет не найден
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error checking auto-start permission: " + e.getMessage());
        }
    }
    
    private void checkPermissionStatus() {
        try {
            android.util.Log.d("MainActivity", "checkPermissionStatus: checking all permissions");
            
            boolean overlayGranted = false;
            boolean batteryOptimizationIgnored = false;
            
            // Проверяем разрешение на оверлей
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                overlayGranted = android.provider.Settings.canDrawOverlays(this);
                android.util.Log.d("MainActivity", "checkPermissionStatus: overlay permission = " + overlayGranted);
            }
            
            // Проверяем игнорирование оптимизации батареи
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    batteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(packageName);
                    android.util.Log.d("MainActivity", "checkPermissionStatus: battery optimization ignored = " + batteryOptimizationIgnored);
                }
            }
            
            // Показываем статус пользователю
            if (!overlayGranted) {
                Toast.makeText(this, "⚠️ Разрешение на оверлей НЕ предоставлено! Оверлей не будет работать.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "✅ Разрешение на оверлей предоставлено", Toast.LENGTH_SHORT).show();
            }
            
            if (!batteryOptimizationIgnored) {
                Toast.makeText(this, "⚠️ Приложение может быть остановлено системой", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "✅ Оптимизация батареи отключена", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error checking permission status: " + e.getMessage());
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        try {
            // Проверяем и перезапускаем сервис при возобновлении активности
            boolean enabled = prefs.getBoolean(KEY_ANTIVIRUS_ENABLED, true);
            if (enabled && !isServiceRunning()) {
                android.util.Log.d("MainActivity", "Service not running on resume, restarting");
                startAntivirus();
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error in onResume: " + e.getMessage());
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Не останавливаем мониторинг при паузе
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Не останавливаем мониторинг при остановке
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        isDestroyed = true;
        
        // Останавливаем мониторинг при уничтожении активности
        if (serviceMonitor != null) {
            serviceMonitor.removeCallbacksAndMessages(null);
        }
    }
    
    /**
     * Проверяет и запрашивает необходимые разрешения
     */
    private void checkAndRequestPermissions() {
        try {
            // Проверяем разрешение на оверлей
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    requestOverlayPermission();
                }
            }
            
            // Проверяем игнорирование оптимизации батареи
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                    requestBatteryOptimizationPermission();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error checking permissions: " + e.getMessage());
        }
    }
    
    /**
     * Запрашивает разрешение на отображение оверлея
     */
    private void requestOverlayPermission() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("Разрешение на всплывающие окна")
                .setMessage("Для работы предупреждений о вирусах необходимо разрешить приложению отображать всплывающие окна поверх других приложений.")
                .setPositiveButton("Настроить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                        } catch (Exception e) {
                            android.util.Log.e("MainActivity", "Error opening overlay settings: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .setCancelable(false)
                .show();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error showing overlay permission dialog: " + e.getMessage());
        }
    }
    
    /**
     * Запрашивает разрешение на игнорирование оптимизации батареи
     */
    private void requestBatteryOptimizationPermission() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("Оптимизация батареи")
                .setMessage("Для стабильной работы антивируса рекомендуется отключить оптимизацию батареи для этого приложения.")
                .setPositiveButton("Настроить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE);
                        } catch (Exception e) {
                            android.util.Log.e("MainActivity", "Error opening battery settings: " + e.getMessage());
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .setCancelable(false)
                .show();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error showing battery optimization dialog: " + e.getMessage());
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    android.util.Log.d("MainActivity", "Overlay permission granted");
                    Toast.makeText(this, "✅ Разрешение на всплывающие окна предоставлено!", Toast.LENGTH_SHORT).show();
                    
                    // Перезапускаем сервис после получения разрешения
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startAntivirus();
                        }
                    }, 1000);
                    
                } else {
                    android.util.Log.e("MainActivity", "Overlay permission denied");
                    Toast.makeText(this, "❌ Разрешение на всплывающие окна НЕ предоставлено! Предупреждения о вирусах не будут работать.", Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == BATTERY_OPTIMIZATION_REQUEST_CODE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) {
                    android.util.Log.d("MainActivity", "Battery optimization permission granted");
                    Toast.makeText(this, "✅ Оптимизация батареи отключена!", Toast.LENGTH_SHORT).show();
                } else {
                    android.util.Log.e("MainActivity", "Battery optimization permission denied");
                    Toast.makeText(this, "⚠️ Оптимизация батареи не отключена. Приложение может быть остановлено системой.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
} 