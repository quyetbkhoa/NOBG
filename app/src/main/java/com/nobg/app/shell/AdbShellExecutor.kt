package com.nobg.app.shell

import android.net.LocalSocket
import android.net.LocalSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class AdbShellExecutor : ShellExecutor {
    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val mutex = Mutex()
    private val socketName = "com.nobg.app.shell"

    fun connect(): Boolean {
        try {
            disconnect()
            val newSocket = LocalSocket()
            newSocket.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            val newOutput = DataOutputStream(newSocket.outputStream)
            val newInput = DataInputStream(newSocket.inputStream)

            val pingBytes = "PING".toByteArray()
            newOutput.writeInt(pingBytes.size)
            newOutput.write(pingBytes)
            newOutput.flush()

            val resLen = newInput.readInt()
            val resBytes = ByteArray(resLen)
            newInput.readFully(resBytes)
            
            if (String(resBytes) == "PONG") {
                socket = newSocket
                output = newOutput
                input = newInput
                return true
            }
            newSocket.close()
            return false
        } catch (e: Exception) {
            return false
        }
    }

    override suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isConnected()) {
                if (!connect()) {
                    return@withContext "ERROR: AdbShellExecutor not connected"
                }
            }

            try {
                return@withLock executeInternal(cmd)
            } catch (e: IOException) {
                if (connect()) {
                    try {
                        return@withLock executeInternal(cmd)
                    } catch (e2: IOException) {
                        disconnect()
                        return@withLock "ERROR: ${e2.message}"
                    }
                }
                return@withLock "ERROR: Reconnection failed"
            } catch (e: Exception) {
                return@withLock "ERROR: ${e.message}"
            }
        }
    }

    private fun executeInternal(cmd: String): String {
        val out = output ?: throw IOException("Not connected")
        val inp = input ?: throw IOException("Not connected")
        
        val cmdBytes = cmd.toByteArray()
        out.writeInt(cmdBytes.size)
        out.write(cmdBytes)
        out.flush()
        
        val resLen = inp.readInt()
        val resBytes = ByteArray(resLen)
        inp.readFully(resBytes)
        
        return String(resBytes)
    }

    override fun isConnected(): Boolean {
        return socket?.isConnected == true
    }

    override fun disconnect() {
        try { input?.close() } catch (e: Exception) {}
        try { output?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        input = null
        output = null
        socket = null
    }
}
