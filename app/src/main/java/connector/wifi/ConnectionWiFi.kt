package com.example.connector.wifi

import android.util.Log
import com.example.connector.common.ConnectionHandler
import com.example.connector.common.ConnectionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class ConnectionWiFi(private val ipAddress: String, private val port: Int) : ConnectionHandler {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val socketAddress = InetSocketAddress(ipAddress, port)
            socket = Socket()
            socket?.connect(socketAddress, 3000)
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()

            Log.d("Connector(WiFi)", "Connected to $ipAddress, $port")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()

            Log.d("Connector(WiFi)", "Disconnected")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(ConnectionUtil.TIMEOUT.toLong()) {
                outputStream?.write(data)
                outputStream?.flush()

                Log.d("Connector(WiFi)", "Data sent: ${String(data).trim()}")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun receiveData(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(ConnectionUtil.BUFFER_SIZE)
            withTimeout(ConnectionUtil.TIMEOUT.toLong()) {
                var receivedData: ByteArray? = null
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead > 0) {
                    receivedData = buffer.copyOf(bytesRead)

                    Log.d("Connector(WiFi)", "Data received: ${String(receivedData).trim()}")
                }
                receivedData
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}