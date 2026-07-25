package com.nobg.app.shell

import android.net.LocalServerSocket
import android.net.LocalSocket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.util.Scanner
import kotlin.concurrent.thread

object AdbDaemonServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val socketName = args.getOrNull(0) ?: "com.nobg.app.shell"
        println("[NOBG] Server starting on socket: $socketName")

        val serverSocket = try {
            LocalServerSocket(socketName)
        } catch (e: Exception) {
            println("[NOBG] Failed to open LocalServerSocket: ${e.message}")
            return
        }

        println("[NOBG] Server listening for connections...")
        while (true) {
            try {
                val clientSocket = serverSocket.accept()
                println("[NOBG] Client connected")
                thread {
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                println("[NOBG] Error accepting connection: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: LocalSocket) {
        var input: DataInputStream? = null
        var output: DataOutputStream? = null
        try {
            input = DataInputStream(socket.inputStream)
            output = DataOutputStream(socket.outputStream)
            
            while (true) {
                val cmdLen = input.readInt()
                val cmdBytes = ByteArray(cmdLen)
                input.readFully(cmdBytes)
                val cmd = String(cmdBytes)
                
                val responseStr = if (cmd == "PING") {
                    "PONG"
                } else {
                    executeShellCommand(cmd)
                }
                
                val responseBytes = responseStr.toByteArray()
                output.writeInt(responseBytes.size)
                output.write(responseBytes)
                output.flush()
            }
        } catch (e: Exception) {
            println("[NOBG] Client disconnected: ${e.message}")
        } finally {
            try { input?.close() } catch (e: Exception) {}
            try { output?.close() } catch (e: Exception) {}
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun executeShellCommand(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = readStream(process.inputStream)
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private fun readStream(inputStream: InputStream): String {
        return try {
            Scanner(inputStream).useDelimiter("\\A").let {
                if (it.hasNext()) it.next() else ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
