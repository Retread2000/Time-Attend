package com.example.timeattend;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "TimeAttendPrefs";
    private static final String KEY_IS_CLOCKED_IN = "isClockedIn";
    private static final String ADMIN_PASSWORD = "1234";

    private TextView statusText;
    private Button btnClockIn;
    private Button btnClockOut;
    private boolean isClockedIn = false;
    private SharedPreferences prefs;

    private DevicePolicyManager dpm;
    private ComponentName deviceAdmin;

    private final Handler lockHandler = new Handler(Looper.getMainLooper());
    private final Runnable lockRunnable = new Runnable() {
        @Override
        public void run() {
            lockScreenIfSettingsIsOpen();
            lockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdmin = new ComponentName(this, MyDeviceAdminReceiver.class);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isClockedIn = prefs.getBoolean(KEY_IS_CLOCKED_IN, false);

        statusText = findViewById(R.id.statusText);
        btnClockIn = findViewById(R.id.btnClockIn);
        btnClockOut = findViewById(R.id.btnClockOut);

        btnClockIn.setOnClickListener(v -> {
            setClockState(true);
            Toast.makeText(this, R.string.msg_clocked_in, Toast.LENGTH_SHORT).show();
        });

        btnClockOut.setOnClickListener(v -> {
            setClockState(false);
            Toast.makeText(this, R.string.msg_clocked_out, Toast.LENGTH_SHORT).show();
        });

        statusText.setOnLongClickListener(v -> {
            showAdminDialog();
            return true;
        });

        updateUI();

        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Please enable Usage Access", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }

        setupKioskMode();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Do nothing to keep the user in the app
            }
        });
    }

    private void showAdminDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.admin_access);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.enter_password);
        builder.setView(input);

        builder.setPositiveButton(R.string.exit_kiosk, (dialog, which) -> {
            String password = input.getText().toString();
            if (ADMIN_PASSWORD.equals(password)) {
                exitKioskMode();
            } else {
                Toast.makeText(this, R.string.invalid_password, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void setupKioskMode() {
        if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
            dpm.setLockTaskPackages(deviceAdmin, new String[]{getPackageName()});
            startLockTask();
        } else {
            startKioskModeFallback();
        }
    }

    private void exitKioskMode() {
        try {
            stopLockTask();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error exiting kiosk mode", e);
        }
    }

    private void startKioskModeFallback() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                startLockTask();
            } catch (Exception e) {
                Log.e(TAG, "Error starting lock task", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lockHandler.post(lockRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        lockHandler.removeCallbacks(lockRunnable);
    }

    private void setClockState(boolean clockedIn) {
        isClockedIn = clockedIn;
        prefs.edit().putBoolean(KEY_IS_CLOCKED_IN, isClockedIn).apply();
        updateUI();
    }

    private void updateUI() {
        if (isClockedIn) {
            statusText.setText(R.string.status_clocked_in);
            btnClockIn.setEnabled(false);
            btnClockOut.setEnabled(true);
        } else {
            statusText.setText(R.string.status_not_clocked_in);
            btnClockIn.setEnabled(true);
            btnClockOut.setEnabled(false);
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void lockScreenIfSettingsIsOpen() {
        String currentApp = "NULL";
        UsageStatsManager usm = (UsageStatsManager) this.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;

        long time = System.currentTimeMillis();
        List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time);
        if (appList != null && !appList.isEmpty()) {
            SortedMap<Long, UsageStats> mySortedMap = new TreeMap<>();
            for (UsageStats usageStats : appList) {
                mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            if (!mySortedMap.isEmpty()) {
                UsageStats lastStats = mySortedMap.get(mySortedMap.lastKey());
                if (lastStats != null) {
                    currentApp = lastStats.getPackageName();
                }
            }
        }

        if ("com.android.settings".equals(currentApp)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        }
    }
}
