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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import java.util.List;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {
    private MaterialButton githubButton, siteButton, telegramButton, checkUpdatesButton;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "quantivirus_prefs";
    private static final String KEY_ANTIVIRUS_ENABLED = "antivirus_enabled";
    private Handler serviceMonitor;
    private static final long MONITOR_INTERVAL = 1000; // 1 секунда
    private boolean isDestroyed = false;
    private UpdateChecker updateChecker;
    
    // Константы для разрешений
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1234;
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 5678;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 9012;
    private static final int INSTALL_PACKAGES_REQUEST_CODE = 3456;
    
    // Ключи для SharedPreferences (чтобы диалоги показывались только один раз)
    private static final String KEY_OVERLAY_PERMISSION_SHOWN = "overlay_permission_shown";
    private static final String KEY_BATTERY_PERMISSION_SHOWN = "battery_permission_shown";
    private static final String KEY_INSTALL_PERMISSION_SHOWN = "install_permission_shown";
    
    // Массив разрешений для загрузки и установки
    private static final String[] DOWNLOAD_PERMISSIONS = {
        android.Manifest.permission.REQUEST_INSTALL_PACKAGES
    };
    
    // Разрешения для старых версий Android (до API 29)
    private static final String[] LEGACY_STORAGE_PERMISSIONS = {
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    };

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
            githubButton = findViewById(R.id.github_button);
            siteButton = findViewById(R.id.site_button);
            telegramButton = findViewById(R.id.telegram_button);
            checkUpdatesButton = findViewById(R.id.check_updates_button);


            
            // Проверяем и запрашиваем необходимые разрешения
            checkAndRequestPermissions();
            
            // Антивирус ВСЕГДА запущен
            startAntivirus();
            
            // Запускаем сервис автономности
            startAutonomyService();
            
            // Запускаем мониторинг сервиса для автономности
            startServiceMonitoring();



            // Настраиваем кнопки
            if (githubButton != null) {
                githubButton.setOnClickListener(v -> {
                    try {
                        openUrl("https://github.com/eh1oy/QUANTIVIRUS/");
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

            if (checkUpdatesButton != null) {
                checkUpdatesButton.setOnClickListener(v -> {
                    try {
                        checkUpdatesButton.setEnabled(false);
                        checkUpdatesButton.setText("Проверяю...");
                        checkForUpdates();
                        Toast.makeText(this, "Проверка обновлений...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error checking updates: " + e.getMessage());
                        Toast.makeText(this, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show();
                        checkUpdatesButton.setEnabled(true);
                        checkUpdatesButton.setText("Проверить обновления");
                    }
                });
            }





            // Запрашиваем разрешения
            requestPermissions();
            
            // Проверяем статус разрешений и показываем пользователю
            checkPermissionStatus();
            
            // Запускаем мониторинг сервиса
            startServiceMonitoring();
            
            // Инициализируем проверку обновлений (без автоматического запуска)
            initUpdateChecker();

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
    

    
    /**
     * Проверяет и запрашивает необходимые разрешения
     */
    private void checkAndRequestPermissions() {
        // Проверяем разрешения для загрузки
        checkDownloadPermissions();
        
        // Проверяем остальные разрешения
        try {
            // Проверяем разрешение на оверлей (показываем диалог только один раз)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this) && !prefs.getBoolean(KEY_OVERLAY_PERMISSION_SHOWN, false)) {
                    requestOverlayPermission();
                }
            }
            
            // Проверяем игнорирование оптимизации батареи (показываем диалог только один раз)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean(KEY_BATTERY_PERMISSION_SHOWN, false)) {
                    requestBatteryOptimizationPermission();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error checking permissions: " + e.getMessage());
        }
    }
    
    /**
     * Проверяет разрешения для загрузки и установки
     */
    private void checkDownloadPermissions() {
        try {
            // Для Android 10+ (API 29+) разрешения на хранилище не нужны для DownloadManager
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                // Проверяем разрешения на хранилище только для старых версий
                boolean legacyPermissionsGranted = true;
                for (String permission : LEGACY_STORAGE_PERMISSIONS) {
                    if (ContextCompat.checkSelfPermission(this, permission) 
                            != PackageManager.PERMISSION_GRANTED) {
                        legacyPermissionsGranted = false;
                        break;
                    }
                }
                
                if (!legacyPermissionsGranted) {
                    ActivityCompat.requestPermissions(this, LEGACY_STORAGE_PERMISSIONS, STORAGE_PERMISSION_REQUEST_CODE);
                }
            }
            
            // Проверяем разрешение на установку пакетов (показываем диалог только один раз)
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.REQUEST_INSTALL_PACKAGES) 
                    != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean(KEY_INSTALL_PERMISSION_SHOWN, false)) {
                // Для Android 6+ запрашиваем разрешение
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    ActivityCompat.requestPermissions(this, DOWNLOAD_PERMISSIONS, INSTALL_PACKAGES_REQUEST_CODE);
                } else {
                    // Для старых версий показываем инструкции
                    showInstallPermissionInstructions();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error checking download permissions: " + e.getMessage());
        }
    }
    
    /**
     * Запрашивает разрешение на отображение оверлея
     */
    private void requestOverlayPermission() {
        try {
            // Сохраняем флаг, что диалог уже показан
            prefs.edit().putBoolean(KEY_OVERLAY_PERMISSION_SHOWN, true).apply();
            
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
            // Сохраняем флаг, что диалог уже показан
            prefs.edit().putBoolean(KEY_BATTERY_PERMISSION_SHOWN, true).apply();
            
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
    
    /**
     * Показывает инструкции для разрешения установки пакетов
     */
    private void showInstallPermissionInstructions() {
        try {
            // Сохраняем флаг, что диалог уже показан
            prefs.edit().putBoolean(KEY_INSTALL_PERMISSION_SHOWN, true).apply();
            
            new AlertDialog.Builder(this)
                .setTitle("Разрешение на установку приложений")
                .setMessage("Для установки обновлений необходимо разрешить установку приложений из неизвестных источников.\n\n" +
                          "1. Перейдите в Настройки → Безопасность\n" +
                          "2. Включите 'Неизвестные источники'\n" +
                          "3. Или разрешите установку для этого приложения")
                .setPositiveButton("Настроить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, INSTALL_PACKAGES_REQUEST_CODE);
                        } catch (Exception e) {
                            // Fallback для старых версий Android
                            try {
                                Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                                startActivity(intent);
                            } catch (Exception e2) {
                                android.util.Log.e("MainActivity", "Error opening security settings: " + e2.getMessage());
                            }
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .setCancelable(false)
                .show();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error showing install permission dialog: " + e.getMessage());
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
    
    /**
     * Сбрасывает флаги показа диалогов разрешений (для отладки)
     */
    private void resetPermissionFlags() {
        try {
            prefs.edit()
                .putBoolean(KEY_OVERLAY_PERMISSION_SHOWN, false)
                .putBoolean(KEY_BATTERY_PERMISSION_SHOWN, false)
                .putBoolean(KEY_INSTALL_PERMISSION_SHOWN, false)
                .apply();
            android.util.Log.d("MainActivity", "Permission flags reset");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error resetting permission flags: " + e.getMessage());
        }
    }
    
    /**
     * Инициализирует проверку обновлений
     */
    private void initUpdateChecker() {
        try {
            updateChecker = new UpdateChecker(this);
            // Автоматическая проверка обновлений при запуске
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                checkForUpdates();
            }, 3000); // Проверка через 3 секунды после запуска
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error initializing update checker: " + e.getMessage());
        }
    }
    
    

    /**
     * Проверяет наличие обновлений
     */
    private void checkForUpdates() {
        try {
            if (updateChecker != null) {
                updateChecker.checkForUpdates(new UpdateChecker.UpdateCallback() {
                    @Override
                    public void onUpdateAvailable(String serverVersion) {
                        checkUpdatesButton.setEnabled(true);
                        checkUpdatesButton.setText("Проверить обновления");
                        showUpdateDialog(serverVersion);
                    }
                    
                    @Override
                    public void onNoUpdateAvailable() {
                        android.util.Log.d("MainActivity", "No updates available");
                        checkUpdatesButton.setEnabled(true);
                        checkUpdatesButton.setText("Проверить обновления");
                        Toast.makeText(MainActivity.this, "✅ У вас установлена последняя версия", Toast.LENGTH_LONG).show();
                    }
                    
                    @Override
                    public void onError(String error) {
                        android.util.Log.e("MainActivity", "Update check error: " + error);
                        checkUpdatesButton.setEnabled(true);
                        checkUpdatesButton.setText("Проверить обновления");
                        Toast.makeText(MainActivity.this, "❌ Ошибка проверки обновлений: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error checking for updates: " + e.getMessage());
            Toast.makeText(this, "❌ Ошибка проверки обновлений", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Показывает диалог обновления
     */
    private void showUpdateDialog(String serverVersion) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update_available, null);
            builder.setView(dialogView);
            
            // Настраиваем тексты версий
            TextView currentVersionText = dialogView.findViewById(R.id.current_version_text);
            TextView newVersionText = dialogView.findViewById(R.id.new_version_text);
            
            String currentVersion = "1.1"; // Текущая версия приложения
            currentVersionText.setText("Текущая версия: " + currentVersion);
            newVersionText.setText("Новая версия: " + serverVersion);
            
            // Настраиваем кнопки
            MaterialButton btnLater = dialogView.findViewById(R.id.btn_later);
            MaterialButton btnUpdate = dialogView.findViewById(R.id.btn_update);
            
            AlertDialog dialog = builder.create();
            dialog.setCancelable(false);
            
            btnLater.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "Обновление отложено", Toast.LENGTH_SHORT).show();
                }
            });
            
            btnUpdate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    startUpdate();
                }
            });
            
            dialog.show();
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error showing update dialog: " + e.getMessage());
        }
    }
    
    /**
     * Запускает процесс обновления
     */
    private void startUpdate() {
        try {
            if (updateChecker != null) {
                updateChecker.downloadUpdate();
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error starting update: " + e.getMessage());
            Toast.makeText(this, "Ошибка запуска обновления", Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        try {
            if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
                boolean allGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
                
                if (allGranted) {
                    Toast.makeText(this, "✅ Разрешения на хранилище получены", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "⚠️ Разрешения на хранилище не получены. Загрузка может не работать на старых версиях Android.", Toast.LENGTH_LONG).show();
                }
            } else if (requestCode == INSTALL_PACKAGES_REQUEST_CODE) {
                boolean allGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
                
                if (allGranted) {
                    Toast.makeText(this, "✅ Разрешение на установку пакетов получено", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "⚠️ Разрешение на установку пакетов не получено. Обновления нужно будет устанавливать вручную.", Toast.LENGTH_LONG).show();
                    showInstallPermissionInstructions();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error handling permission result: " + e.getMessage());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        isDestroyed = true;
        
        // Останавливаем мониторинг при уничтожении активности
        if (serviceMonitor != null) {
            serviceMonitor.removeCallbacksAndMessages(null);
        }
        
        // Останавливаем проверку обновлений
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
    }
} 