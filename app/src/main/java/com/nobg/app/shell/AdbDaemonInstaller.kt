package com.nobg.app.shell

import android.content.Context
import java.io.File

object AdbDaemonInstaller {
    private const val SCRIPT_NAME = "nobg_start.sh"
    private const val SOCKET_NAME = "com.nobg.app.shell"
    
    fun extractDaemonScript(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val scriptFile = File(dir, SCRIPT_NAME)
        context.assets.open("nobg_daemon.sh").use { input ->
            scriptFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return scriptFile
    }
    
    fun getWiredAdbCommand(context: Context): String {
        val scriptPath = getScriptPath(context)
        return "adb shell sh $scriptPath"
    }
    
    fun getWirelessAdbPairInstructions(): String {
        return """1. Vào Cài đặt → Tùy chọn nhà phát triển → Gỡ lỗi không dây
2. Bật "Gỡ lỗi không dây" → Nhấn "Ghép nối thiết bị bằng mã ghép nối"
3. Mở Terminal trên PC, gõ: adb pair <IP:PORT> rồi nhập mã ghép nối
4. Sau khi ghép nối thành công, gõ: adb connect <IP:PORT>
5. Chạy lệnh bên dưới để khởi động NOBG daemon"""
    }
    
    fun getShellCommand(context: Context): String {
        val scriptPath = getScriptPath(context)
        return "sh $scriptPath"
    }
    
    private fun getScriptPath(context: Context): String {
        val dir = context.getExternalFilesDir(null)
        return if (dir != null) {
            "${dir.absolutePath}/$SCRIPT_NAME"
        } else {
            "/data/data/${context.packageName}/files/$SCRIPT_NAME"
        }
    }
    
    fun isDaemonRunning(): Boolean {
        return try {
            val socket = android.net.LocalSocket()
            socket.connect(android.net.LocalSocketAddress(SOCKET_NAME, android.net.LocalSocketAddress.Namespace.ABSTRACT))
            val out = java.io.DataOutputStream(socket.outputStream)
            val inp = java.io.DataInputStream(socket.inputStream)
            
            val pingBytes = "PING".toByteArray()
            out.writeInt(pingBytes.size)
            out.write(pingBytes)
            out.flush()
            
            val resLen = inp.readInt()
            val resBytes = ByteArray(resLen)
            inp.readFully(resBytes)
            socket.close()
            
            String(resBytes) == "PONG"
        } catch (e: Exception) {
            false
        }
    }
}
