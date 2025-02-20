package com.example.connector.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.connector.common.ConnectionHandler
import com.example.connector.common.ConnectionUtil
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ConnectionBT(private val bluetoothDevice: BluetoothDevice? = null) : ConnectionHandler {
    private var bluetoothSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var uuid = "00001101-0000-1000-8000-00805F9B34FB" // SPP

    @SuppressLint("MissingPermission")
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream

            Log.d("Connector(BT)", "Connected in CLIENT")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun listen(): Boolean = withContext(Dispatchers.IO) {
        try {
            serverSocket = BluetoothAdapter.getDefaultAdapter()
                .listenUsingRfcommWithServiceRecord("MyBluetoothApp", UUID.fromString(uuid))
            bluetoothSocket = serverSocket?.accept()
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream

            Log.d("Connector(BT)", "Connected in SERVER")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    override fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            serverSocket?.close()
            inputStream = null
            outputStream = null
            bluetoothSocket = null
            serverSocket = null

            Log.d("Connector(BT)", "Disconnected from Bluetooth")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(data)
            outputStream?.flush()

            Log.d("Connector(BT)", "Data sent: ${String(data).trim()}")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun receiveData(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(ConnectionUtil.BUFFER_SIZE)
            val bytesRead = inputStream?.read(buffer) ?: -1
            if (bytesRead > 0) {
                val receivedData = buffer.copyOf(bytesRead)

                Log.d("Connector(BT)", "Data received: ${String(receivedData).trim()}")
                return@withContext receivedData
            }
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}