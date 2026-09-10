# Time Attend
> **Professional Kiosk Employee Timeclock for Android Devices**

Time Attend transforms a standard Android Device into a high-security, dedicated time-tracking station. Built with **Material 3**, it offers a seamless experience for employees while maintaining a "locked-down" environment that prevents unauthorized device access.

---

## Quick Reference
| Feature | Details |
| :--- | :--- |
| **Min. Android Version** | SDK 24 (Android 7.0) |
| **Security Mode** | Device Owner (True Kiosk) |

---

## Key Features

*   ** Consolidated Toggle**: A smart single-button interface that detects if an employee needs to clock in or out based on their history.
*   ** Material 3 UI**: A legible, tablet-optimized layout featuring modern Material Design components and Anchored Admin controls.
*   ** Hardened Kiosk Mode**: Disables the status bar, notifications, and navigation swipes (Requires Device Owner status).
*   ** Boot-to-App**: The application launches immediately upon system startup.

---

## Installation & Critical Setup

> [!IMPORTANT]
> To enable full lockdown (disabling swipes and the system lock screen), the app **must** be promoted to **Device Owner** status via ADB.

### 1. Prepare the Device
1.  Go to **Settings > Passwords & Accounts**.
2.  **Remove all accounts** (Google, Email, etc.). Android security prevents setting a Device Owner if any accounts exist.
3.  Enable **USB Debugging** in Developer Options.

### 2. Set Device Owner
Connect the device to your computer and execute the following command in PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell dpm set-device-owner com.example.timeattend/.MyDeviceAdminReceiver
