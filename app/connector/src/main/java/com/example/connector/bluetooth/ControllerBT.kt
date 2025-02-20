package com.example.connector.bluetooth

import android.bluetooth.BluetoothDevice
import com.example.connector.common.BaseController
import com.example.connector.common.ConnectionUtil.ConnectionType

class ControllerBT : BaseController() {
    private var connectionBT: ConnectionBT? = null

    suspend fun connectTo(bluetoothDevice: BluetoothDevice): Boolean {
        connectionBT = ConnectionBT(bluetoothDevice)
        return startConnection()
    }

    suspend fun listenTo(): Boolean {
        connectionBT = ConnectionBT()
        return connectionBT!!.listen().also { isConnected ->
            if (isConnected) connectionType = ConnectionType.BT
        }
    }

    override suspend fun connect(): Boolean {
        return (connectionBT?.connect() ?: false).also { isConnected ->
            if (isConnected) connectionType = ConnectionType.BT
        }
    }

    override fun disconnect() {
        connectionBT?.disconnect()
        connectionType = ConnectionType.NONE
    }

    override suspend fun sendData(data: ByteArray): Boolean {
        return connectionBT?.sendData(data) ?: false
    }

    override suspend fun receiveData(): ByteArray? {
        return connectionBT?.receiveData()
    }
}