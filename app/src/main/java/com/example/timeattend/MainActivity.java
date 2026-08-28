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
import java.util.Calendar;
import java.util.Locale;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String ADMIN_PASSWORD = "1234";

    private TextView statusText;
    private EditText pinInput;
    private Button btnClockIn;
    private Button btnClockOut;

    private DevicePolicyManager dpm;
    private ComponentName deviceAdmin;

    // Database fields
    private AppDatabase db;
    private AttendanceDao attendanceDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

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

        // Initialize Database
        db = AppDatabase.getDatabase(this);
        attendanceDao = db.attendanceDao();

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdmin = new ComponentName(this, MyDeviceAdminReceiver.class);

        statusText = findViewById(R.id.statusText);
        pinInput = findViewById(R.id.pinInput);
        btnClockIn = findViewById(R.id.btnClockIn);
        btnClockOut = findViewById(R.id.btnClockOut);

        btnClockIn.setOnClickListener(v -> processClockEvent(true));
        btnClockOut.setOnClickListener(v -> processClockEvent(false));

        statusText.setOnLongClickListener(v -> {
            showAdminDialog();
            return true;
        });

        // Initialize with default state
        updateUI(null, false);
        bootstrapTestEmployee();

        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, R.string.msg_usage_access, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }

        setupKioskMode();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Keep the user in the app
            }
        });
    }

    private void bootstrapTestEmployee() {
        executorService.execute(() -> {
            if (attendanceDao.getAllEmployees().isEmpty()) {
                attendanceDao.insertEmployee(new Employee("Test User", "1234"));
            }
        });
    }

    private void processClockEvent(boolean isClockIn) {
        String pin = pinInput.getText().toString();
        if (pin.isEmpty()) {
            Toast.makeText(this, R.string.msg_enter_pin, Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            Employee employee = attendanceDao.getEmployeeByBadgeId(pin);

            if (employee == null) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, R.string.msg_invalid_pin, Toast.LENGTH_SHORT).show();
                    pinInput.setText("");
                });
                return;
            }

            // check for current status
            if (employee.isClockedIn == isClockIn) {
                runOnUiThread(() -> {
                    String action = getString(isClockIn ? R.string.label_clocked_in : R.string.label_clocked_out);
                    Toast.makeText(MainActivity.this, getString(R.string.msg_already_clocked, employee.name, action), Toast.LENGTH_SHORT).show();
                    pinInput.setText("");
                });
                return;
            }

            // Initialize local variables for calculating time clocked in
            long currentTime = System.currentTimeMillis();
            long duration = 0;

            // Calculate duration upon clocking out
            if (!isClockIn) {
                AttendanceRecord lastRecord = attendanceDao.getLastRecordForEmployee(employee.id);
                if (lastRecord != null && "IN".equals(lastRecord.type)) {
                    duration = (currentTime - lastRecord.timestamp) / 1000;
                }
            }
            final long clockInDuration = duration;

            // Update Employee
            employee.isClockedIn = isClockIn;
            attendanceDao.updateEmployee(employee);

            // Record Attendance
            AttendanceRecord record = new AttendanceRecord(employee.id, currentTime, isClockIn ? "IN" : "OUT");
            record.timeLogged = clockInDuration;
            attendanceDao.insertRecord(record);

            // Calculate Weekly Total Time
            long weekStart = getStartOfWeekTimestamp();
            long weeklyTotalSeconds = attendanceDao.getWeeklyTimeLogged(employee.id, weekStart);
            String weeklyTotalStr = formatDuration(weeklyTotalSeconds);

            runOnUiThread(() -> {
                pinInput.setText("");
                if (isClockIn) {
                    String msg = getString(R.string.msg_success_clock_in, employee.name);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                } else {
                    String msg = getString(R.string.msg_success_clock_out, employee.name, formatDuration(clockInDuration), weeklyTotalStr);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void updateUI(String employeeName, boolean isClockedIn) {
        statusText.setText(R.string.initial_status);
        btnClockIn.setEnabled(true);
        btnClockOut.setEnabled(true);
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

    private long getStartOfWeekTimestamp() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
