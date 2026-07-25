#!/system/bin/sh
#
# NOBG ADB Daemon Launcher
# ==========================
# Chạy script này qua ADB để cấp quyền shell cho NOBG.
#
# Cách dùng (Wired ADB từ PC):
#   adb shell sh /sdcard/Android/data/com.nobg.app/files/nobg_start.sh
#
# Cách dùng (Wireless ADB):
#   1. Ghép nối thiết bị: adb pair <IP:PORT>
#   2. Kết nối: adb connect <IP:PORT>
#   3. Chạy: adb shell sh /sdcard/Android/data/com.nobg.app/files/nobg_start.sh
#
# Nhấn Ctrl+C hoặc đóng terminal để dừng daemon.
# Sau khi reboot cần chạy lại lệnh này.
#

PKG="com.nobg.app"
SOCKET="com.nobg.app.shell"

# Tìm đường dẫn APK
APK=$(pm path "$PKG" 2>/dev/null | head -1 | sed 's/package://')
if [ -z "$APK" ]; then
    echo "[NOBG] Lỗi: Ứng dụng NOBG chưa được cài đặt!"
    exit 1
fi

# Dừng daemon cũ nếu đang chạy
pkill -f "nobg\.daemon" 2>/dev/null
sleep 0.5

echo "============================================="
echo "  NOBG ADB Daemon"
echo "============================================="
echo "[NOBG] APK: $APK"
echo "[NOBG] Socket: $SOCKET"
echo "[NOBG] Đang khởi động daemon..."
echo "[NOBG] Nhấn Ctrl+C để dừng."
echo "============================================="

# Khởi chạy daemon qua app_process
# app_process chạy class Java/Kotlin từ APK với quyền shell UID
exec app_process \
    -Djava.class.path="$APK" \
    /system/bin \
    --nice-name=nobg.daemon \
    com.nobg.app.shell.AdbDaemonServer "$SOCKET"
