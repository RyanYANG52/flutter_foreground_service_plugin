package changjoopark.com.flutter_foreground_plugin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;


public class FlutterForegroundService extends Service {
    public static int ONGOING_NOTIFICATION_ID = 1;
    public static final String ACTION_STOP_SERVICE = "STOP";
    public static final String WAKELOCK_TAG = "FlutterForegroundService::WakeLock";

    private boolean userStopForegroundService = false;
    private PowerManager.WakeLock wl = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() == null) {
            return START_STICKY;
        }

        final String action = intent.getAction();

        switch (action) {
            case FlutterForegroundPlugin.START_FOREGROUND_ACTION:
                PackageManager pm = getApplicationContext().getPackageManager();
                Intent notificationIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                        notificationIntent, 0);

                Bundle bundle = intent.getExtras();

                String channelId = bundle.getString("channelId");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(channelId,
                            bundle.getString("channelName"),
                            NotificationManager.IMPORTANCE_HIGH);

                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                            .createNotificationChannel(channel);
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(getNotificationIcon(bundle.getString("icon")))
                        .setColor(bundle.getInt("color"))
                        .setContentTitle(bundle.getString("title"))
                        .setContentText(bundle.getString("content"))
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setContentIntent(pendingIntent)
                        .setUsesChronometer(bundle.getBoolean("chronometer"))
                        .setOngoing(true);

                if (bundle.getBoolean("stop_action")) {
                    Intent stopSelf = new Intent(this, FlutterForegroundService.class);
                    stopSelf.setAction(ACTION_STOP_SERVICE);

                    PendingIntent pStopSelf = PendingIntent
                            .getService(this, 0, stopSelf ,PendingIntent.FLAG_CANCEL_CURRENT);
                    builder.addAction(getNotificationIcon(bundle.getString("stop_icon")),
                            bundle.getString("stop_text"),
                            pStopSelf);
                }

                if (bundle.getString("subtext") != null && !bundle.getString("subtext").isEmpty()) {
                    builder.setSubText(bundle.getString("subtext"));
                }

                if (bundle.getBoolean("holdWakeLock")) {
                    PowerManager powerManager =((PowerManager) getSystemService(POWER_SERVICE));
                    wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
                    wl.acquire();
                    android.util.Log.d("FlutterForegroundService", "WAKE_LOCK acquired");
                }

                startForeground(ONGOING_NOTIFICATION_ID, builder.build());
                break;
            case FlutterForegroundPlugin.STOP_FOREGROUND_ACTION:
                stopFlutterForegroundService();
                break;
            case ACTION_STOP_SERVICE:
                stopFlutterForegroundService();
                break;
            default:
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.d("FlutterForegroundService", "onDestroy");
        if (!userStopForegroundService) {
            android.util.Log.d("FlutterForegroundService", "User close app, kill current process to avoid memory leak in other plugin.");
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int getNotificationIcon(String iconName) {
        if(iconName != null && !iconName.isEmpty()){
            return getApplicationContext().getResources().getIdentifier(iconName, "drawable", getApplicationContext().getPackageName());
        }else{
            return getApplicationContext().getResources().getIdentifier("ic_launcher", "mipmap", getApplicationContext().getPackageName());
        }
    }

    private void stopFlutterForegroundService() {
        userStopForegroundService = true;
        if (wl != null){
            android.util.Log.d("FlutterForegroundService", "WAKE_LOCK released");
            wl.release();
            wl = null;
        }
        stopForeground(true);
        stopSelf();
    }
}
