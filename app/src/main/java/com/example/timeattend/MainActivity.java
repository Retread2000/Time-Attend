package com.example.timeattend;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.KeyguardManager;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.os.Build;
import android.os.Bundle;

import android.text.InputType;

import android.util.Log;

import android.view.ViewGroup;

import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.activity.OnBackPressedCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String ADMIN_PASSWORD = "1234";

    private TextView statusText;
    private EditText pinInput;
    private Button btnClockToggle;
    private Button btnAdmin;

    private DevicePolicyManager dpm;
    private ComponentName deviceAdmin;

    // Database fields
    private AppDatabase db;
    private AttendanceDao attendanceDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Database
        db = AppDatabase.getDatabase(this);
        attendanceDao = db.attendanceDao();

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdmin = new ComponentName(this, MyDeviceAdminReceiver.class);

        // Bypass lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        statusText = findViewById(R.id.statusText);
        pinInput = findViewById(R.id.pinInput);
        btnClockToggle = findViewById(R.id.btnClockToggle);
        btnAdmin = findViewById(R.id.btnAdmin);


        btnClockToggle.setOnClickListener(v -> processClockEvent());
        btnAdmin.setOnClickListener(v -> showAdminDialog());

        // Initialize with default state
        bootstrapTestEmployee();

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

    private void processClockEvent() {
        String pin = pinInput.getText().toString();
        if (pin.isEmpty()) {
            statusText.setText(R.string.msg_enter_pin);
            return;
        }

        executorService.execute(() -> {
            Employee employee = attendanceDao.getEmployeeByBadgeId(pin);

            if (employee == null) {
                runOnUiThread(() -> {
                    statusText.setText(R.string.msg_invalid_pin);
                    pinInput.setText("");
                });
                return;
            }

            // Toggle logic: If clocked in, clock out. If clocked out, clock in.
            boolean isClockIn = !employee.isClockedIn;
            long currentTime = System.currentTimeMillis();
            long duration = 0;

            if (!isClockIn) {
                AttendanceRecord lastRecord = attendanceDao.getLastRecordForEmployee(employee.id);
                if (lastRecord != null && "IN".equals(lastRecord.type)) {
                    duration = (currentTime - lastRecord.timestamp) / 1000;
                }
            }

            employee.isClockedIn = isClockIn;
            attendanceDao.updateEmployee(employee);

            AttendanceRecord record = new AttendanceRecord(employee.id, currentTime, isClockIn ? "IN" : "OUT");
            record.timeLogged = duration;
            attendanceDao.insertRecord(record);

            runOnUiThread(() -> {
                pinInput.setText("");
                String msg = getString(isClockIn ? R.string.msg_success_clock_in : R.string.msg_success_clock_out, employee.name);
                statusText.setText(msg);

                // Reset to prompt after 3 seconds
                statusText.postDelayed(() -> statusText.setText(R.string.initial_status), 3000);
            });
        });
    }

    private void showAdminDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.admin_access);
        builder.setCancelable(false); // Prevents accidental closing

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.enter_password);

        // Wrap EditText in a FrameLayout to add padding/margins
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        params.topMargin = margin / 4;
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton(R.string.exit_kiosk, (dialog, which) -> {
            String password = input.getText().toString();
            if (ADMIN_PASSWORD.equals(password)) {
                exitKioskMode();
            } else {
                Toast.makeText(this, R.string.invalid_password, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    private void setupKioskMode() {
        if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
            try {
                // Check if the admin component is actually active
                if (dpm.isAdminActive(deviceAdmin)) {
                    dpm.setLockTaskPackages(deviceAdmin, new String[]{getPackageName()});
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dpm.setLockTaskFeatures(deviceAdmin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
                    }
                } else {
                    Log.w(TAG, "Device admin is not active. Kiosk mode will have limited functionality.");
                }
                // startLockTask(); // Temporarily commented out for testing
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException: App is not recognized as Device Owner: " + e.getMessage());
                // Fallback to basic lock task mode
                startKioskModeFallback();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in setupKioskMode: " + e.getMessage());
            }
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
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
