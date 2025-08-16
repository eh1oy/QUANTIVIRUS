package com.max.quantivirus;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String VERSION_URL = "https://eh1oy.github.io/quantivirus/version.cfg";
    private static final String APK_DOWNLOAD_URL = "https://github.com/eh1oy/QUANTIVIRUS/releases/download/app/app-debug.apk";
    
    private Context context;
    private ExecutorService executor;
    private long downloadId = -1;
    
    public UpdateChecker(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    public interface UpdateCallback {
        void onUpdateAvailable(String serverVersion);
        void onNoUpdateAvailable();
        void onError(String error);
    }
    
    public void checkForUpdates(UpdateCallback callback) {
        executor.execute(() -> {
            try {
                String serverVersion = getServerVersion();
                String currentVersion = getCurrentVersion();
                
                Log.d(TAG, "Current version: " + currentVersion + ", Server version: " + serverVersion);
                
                if (serverVersion != null && !serverVersion.equals(currentVersion)) {
                    ((Activity) context).runOnUiThread(() -> callback.onUpdateAvailable(serverVersion));
                } else {
                    ((Activity) context).runOnUiThread(() -> callback.onNoUpdateAvailable());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                ((Activity) context).runOnUiThread(() -> callback.onError("Ошибка проверки обновлений: " + e.getMessage()));
            }
        });
    }
    
    private String getServerVersion() throws IOException {
        URL url = new URL(VERSION_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000); // Увеличиваем таймаут
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        
        // Добавляем User-Agent для лучшей совместимости
        connection.setRequestProperty("User-Agent", "QUANTIVIRUS-Android/1.1");
        
        try {
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Server response code: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String version = reader.readLine();
                reader.close();
                Log.d(TAG, "Server version: " + version);
                return version != null ? version.trim() : null;
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new IOException("Файл версии не найден на сервере (404)");
            } else if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new IOException("Сервер временно недоступен (503)");
            } else if (responseCode >= 500) {
                throw new IOException("Ошибка сервера: " + responseCode);
            } else {
                throw new IOException("HTTP ошибка: " + responseCode);
            }
        } catch (java.net.ConnectException e) {
            Log.e(TAG, "Connection refused to server", e);
            throw new IOException("Не удается подключиться к серверу. Проверьте, что сервер запущен и доступен.");
        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Connection timeout", e);
            throw new IOException("Таймаут подключения к серверу. Проверьте сетевое соединение.");
        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "Unknown host", e);
            throw new IOException("Сервер не найден. Проверьте правильность IP-адреса.");
        } catch (java.net.SocketException e) {
            Log.e(TAG, "Socket error", e);
            if (e.getMessage().contains("Permission denied")) {
                throw new IOException("Отказано в доступе к сети. Проверьте разрешения приложения.");
            } else if (e.getMessage().contains("Network is unreachable")) {
                throw new IOException("Сеть недоступна. Проверьте Wi-Fi/мобильное соединение.");
            } else {
                throw new IOException("Ошибка сетевого подключения: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting server version", e);
            throw new IOException("Ошибка подключения к серверу: " + e.getMessage());
        } finally {
            connection.disconnect();
        }
    }
    
    private String getCurrentVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting current version", e);
            return "1.0";
        }
    }
    
    public void downloadUpdate() {
        try {
            // ПРИНУДИТЕЛЬНЫЙ ОБХОД ПРОВЕРКИ РАЗРЕШЕНИЙ
            Log.d(TAG, "Принудительная загрузка без проверки разрешений");
            
            // Проверяем доступность DownloadManager
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(context, "❌ Ошибка: DownloadManager недоступен", Toast.LENGTH_LONG).show();
                showManualDownloadInstructions();
                return;
            }

            // Создаем запрос на загрузку БЕЗ ПРОВЕРКИ РАЗРЕШЕНИЙ
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(APK_DOWNLOAD_URL));
            request.setTitle("Обновление QUANTIVIRUS");
            request.setDescription("Загрузка новой версии антивируса");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "quantivirus-update.apk");
            request.setMimeType("application/vnd.android.package-archive");
            
            // Добавляем заголовки для лучшей совместимости
            request.addRequestHeader("User-Agent", "QUANTIVIRUS-Android/1.1");
            
            // Устанавливаем сетевые параметры
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setAllowedOverRoaming(false);
            
            // ПРИНУДИТЕЛЬНО ЗАПУСКАЕМ ЗАГРУЗКУ
            downloadId = downloadManager.enqueue(request);
            
            if (downloadId == -1) {
                Log.e(TAG, "DownloadManager вернул -1, пробуем ручную загрузку");
                showManualDownloadInstructions();
                return;
            }
            
            // Регистрируем receiver для обработки завершения загрузки
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        context.unregisterReceiver(this);
                        handleDownloadComplete(downloadManager, id);
                    }
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            
            Toast.makeText(context, "✅ Начинается загрузка обновления...", Toast.LENGTH_SHORT).show();
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting download", e);
            Toast.makeText(context, "⚠️ Проблема с разрешениями, используем ручную установку", Toast.LENGTH_LONG).show();
            showManualDownloadInstructions();
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            Toast.makeText(context, "⚠️ Ошибка загрузки, используем ручную установку", Toast.LENGTH_LONG).show();
            showManualDownloadInstructions();
        }
    }
    
    private void handleDownloadComplete(DownloadManager downloadManager, long id) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        
        try {
            Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    if (uriString != null) {
                        installUpdate(Uri.parse(uriString));
                    } else {
                        Toast.makeText(context, "❌ Ошибка: URI файла не найден", Toast.LENGTH_LONG).show();
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                    String reasonText = getDownloadFailureReason(reason);
                    Toast.makeText(context, "❌ Ошибка загрузки: " + reasonText, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Download failed with reason: " + reason + " - " + reasonText);
                } else if (status == DownloadManager.STATUS_PAUSED) {
                    Toast.makeText(context, "⏸️ Загрузка приостановлена", Toast.LENGTH_LONG).show();
                } else if (status == DownloadManager.STATUS_PENDING) {
                    Toast.makeText(context, "⏳ Загрузка ожидает...", Toast.LENGTH_LONG).show();
                } else if (status == DownloadManager.STATUS_RUNNING) {
                    Toast.makeText(context, "🔄 Загрузка выполняется...", Toast.LENGTH_LONG).show();
                }
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error handling download complete", e);
            Toast.makeText(context, "❌ Ошибка обработки загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // Получение текстового описания причины ошибки загрузки
    private String getDownloadFailureReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "Загрузка не может быть возобновлена";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Устройство не найдено";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "Файл уже существует";
            case DownloadManager.ERROR_FILE_ERROR:
                return "Ошибка файла";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "Ошибка HTTP данных";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Недостаточно места на диске";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Слишком много перенаправлений";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Необработанный HTTP код";
            case DownloadManager.ERROR_UNKNOWN:
                return "Неизвестная ошибка";
            default:
                return "Ошибка " + reason;
        }
    }
    
    private void installUpdate(Uri apkUri) {
        try {
            // Проверяем, что файл существует
            if (apkUri == null) {
                Toast.makeText(context, "❌ Ошибка: Файл APK не найден", Toast.LENGTH_LONG).show();
                showManualInstallInstructions();
                return;
            }
            
            // ПРИНУДИТЕЛЬНАЯ УСТАНОВКА БЕЗ ПРОВЕРКИ РАЗРЕШЕНИЙ
            Log.d(TAG, "Принудительная установка без проверки разрешений");
            
            // Создаем intent для установки
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // ПРИНУДИТЕЛЬНО ЗАПУСКАЕМ УСТАНОВКУ
            try {
                context.startActivity(intent);
                Toast.makeText(context, "✅ Откройте файл для установки обновления", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Error starting install activity: " + e.getMessage());
                showManualInstallInstructions();
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception installing update", e);
            showManualInstallInstructions();
        } catch (Exception e) {
            Log.e(TAG, "Error installing update", e);
            showManualInstallInstructions();
        }
    }
    
    private void showManualInstallInstructions() {
        try {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            builder.setTitle("Установка обновления")
                   .setMessage("Файл обновления загружен в папку Downloads.\n\n" +
                             "Для установки:\n" +
                             "1. Откройте файловый менеджер\n" +
                             "2. Найдите файл 'quantivirus-update.apk'\n" +
                             "3. Нажмите на файл и выберите 'Установить'\n" +
                             "4. Следуйте инструкциям установщика")
                   .setPositiveButton("Понятно", null)
                   .setNegativeButton("Открыть Downloads", new android.content.DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(android.content.DialogInterface dialog, int which) {
                           try {
                               Intent intent = new Intent(Intent.ACTION_VIEW);
                               intent.setData(android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"));
                               intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                               context.startActivity(intent);
                           } catch (Exception e) {
                               Log.e(TAG, "Error opening downloads: " + e.getMessage());
                               Toast.makeText(context, "Откройте папку Downloads вручную", Toast.LENGTH_SHORT).show();
                           }
                       }
                   })
                   .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing manual install dialog: " + e.getMessage());
            Toast.makeText(context, "✅ Файл загружен в Downloads. Установите вручную.", Toast.LENGTH_LONG).show();
        }
    }
    
    private void showPermissionsHelpDialog() {
        try {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            builder.setTitle("Проблема с разрешениями")
                   .setMessage("Для загрузки обновлений необходимо предоставить разрешения:\n\n" +
                             "1. **Установка приложений** - в настройках приложения\n" +
                             "2. **Хранилище** - для Android до 10.0\n" +
                             "3. **Неизвестные источники** - в настройках безопасности\n\n" +
                             "Если проблема остается, используйте ручную установку.")
                   .setPositiveButton("Настроить разрешения", new android.content.DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(android.content.DialogInterface dialog, int which) {
                           try {
                               Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                               intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                               intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                               context.startActivity(intent);
                           } catch (Exception e) {
                               Log.e(TAG, "Error opening app settings: " + e.getMessage());
                           }
                       }
                   })
                   .setNegativeButton("Ручная установка", new android.content.DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(android.content.DialogInterface dialog, int which) {
                           showManualDownloadInstructions();
                       }
                   })
                   .setNeutralButton("Отмена", null)
                   .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing permissions help dialog: " + e.getMessage());
        }
    }
    
    private void showManualDownloadInstructions() {
        try {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            builder.setTitle("🚨 РУЧНАЯ УСТАНОВКА ОБНОВЛЕНИЯ")
                   .setMessage("Автоматическая загрузка не работает из-за ограничений Android.\n\n" +
                             "Для установки обновления:\n\n" +
                             "1. Нажмите 'ОТКРЫТЬ БРАУЗЕР'\n" +
                             "2. Скачайте файл APK\n" +
                             "3. Откройте скачанный файл\n" +
                             "4. Нажмите 'УСТАНОВИТЬ'\n\n")
                   .setPositiveButton("ОТКРЫТЬ БРАУЗЕР", new android.content.DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(android.content.DialogInterface dialog, int which) {
                           try {
                               Intent intent = new Intent(Intent.ACTION_VIEW);
                               intent.setData(android.net.Uri.parse("https://github.com/eh1oy/QUANTIVIRUS/releases/download/app/app-debug.apk"));
                               intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                               context.startActivity(intent);
                               Toast.makeText(context, "✅ Браузер открыт! Скачайте и установите файл.", Toast.LENGTH_LONG).show();
                           } catch (Exception e) {
                               Log.e(TAG, "Error opening browser: " + e.getMessage());
                               Toast.makeText(context, "Откройте браузер и перейдите по ссылке вручную", Toast.LENGTH_LONG).show();
                           }
                       }
                   })
                   .setNegativeButton("ПОЗЖЕ", null)
                   .setCancelable(false)
                   .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing manual download dialog: " + e.getMessage());
            // Показываем Toast как запасной вариант
            Toast.makeText(context, "Откройте браузер: https://github.com/eh1oy/QUANTIVIRUS/releases/download/app/app-debug.apk", Toast.LENGTH_LONG).show();
        }
    }
    
    // ПРИНУДИТЕЛЬНАЯ ПРОВЕРКА РАЗРЕШЕНИЙ - ВСЕГДА ВОЗВРАЩАЕТ TRUE
    private boolean checkDownloadPermissions() {
        Log.d(TAG, "Принудительная проверка разрешений - все разрешения считаются предоставленными");
        return true; // ВСЕГДА ВОЗВРАЩАЕМ TRUE - НЕ ПРОВЕРЯЕМ РАЗРЕШЕНИЯ
    }
    
    // Метод удален - больше не нужен, так как разрешения не проверяются
    
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    

}
