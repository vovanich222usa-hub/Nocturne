# Nocturne 1.2

Android alarm clock and personal planner. This project is based on the supplied working Nocturne 1.1 source tree.

## What's updated
- Alarm audio is played by a foreground service with `MediaPlayer` and Android alarm audio attributes. Built-in sounds are embedded WAV resources and loop until an action is chosen.
- User-selected audio files are opened with `ACTION_OPEN_DOCUMENT`; persisted URI read permission is retained for later alarm playback.
- Each alarm can use gradual volume increase or immediate full app playback volume. The phone's system alarm volume still applies.
- A persistent foreground notification displays the next scheduled items and offers Stop (red), Cancel current occurrence (orange), and Snooze 5 minutes (blue).
- Time input has an HH:MM mask and a time picker. Date is selected using the Android calendar picker.
- Alarms are restored after reboot, app update, time changes and time-zone changes.

## Build
GitHub Actions workflow: `.github/workflows/android.yml`.
It builds `:App:assembleDebug` and uploads `Nocturne-debug` as an artifact.

## Android permissions and device settings
On first launch, allow notifications and exact alarms when Android asks. On TECNO/HiOS, also allow Nocturne to run in the background and disable battery optimization/automatic app cleanup for Nocturne; vendor battery management may otherwise stop background services. Keep system alarm volume audible. The app works locally and does not require internet.

## Notification controls
- 🔴 Stop: stops the Nocturne background service and current sound.
- 🟠 Cancel: cancels the currently ringing one-time alarm; for a repeating alarm it silences this occurrence while keeping the next occurrence scheduled.
- 🔵 +5 min: silences the current sound and schedules it again in five minutes.

The persistent notification is shown after Nocturne has been opened at least once and notification permission is granted. Android may hide notification content if notification permission is denied.
