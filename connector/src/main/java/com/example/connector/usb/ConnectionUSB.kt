package com.example.connector.usb

import android.util.Log
import com.example.connector.common.ConnectionHandler
import com.example.connector.common.ConnectionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class ConnectionUSB(private val port: Int) : ConnectionHandler {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val socketAddress = InetSocketAddress("127.0.0.1", port)
            socket = Socket()
            socket?.connect(socketAddress, 3000)
            outputStream = socket?.getOutputStream()
            inputStream = socket?.getInputStream()

            Log.d("Connector(USB)", "Connected to 127.0.0.1:$port")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    override fun disconnect() {
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()

            Log.d("Connector(USB)", "Disconnected")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(data)
            outputStream?.flush()
            Log.d("Connector(USB)", "Data sent: ${String(data)}")
            true
        } catch (e: IOException) {
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

                    Log.d("Connector(USB)", "Data received: ${String(receivedData).trim()}")
                }
                receivedData
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}