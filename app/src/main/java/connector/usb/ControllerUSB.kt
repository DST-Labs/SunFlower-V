package com.example.connector.usb

import com.example.connector.common.BaseController
import com.example.connector.common.ConnectionUtil

class ControllerUSB : BaseController() {
    private var connectionUSB: ConnectionUSB? = null

    suspend fun connectTo(port: Int): Boolean {
        connectionUSB = ConnectionUSB(port)
        return startConnection()
    }

    override suspend fun connect(): Boolean {
        return (connectionUSB?.connect() ?: false)
            .also { isConnected ->
                if (isConnected) connectionType = ConnectionUtil.ConnectionType.USB
            }
    }

    override fun disconnect() {
        connectionUSB?.disconnect()
        connectionType = ConnectionUtil.ConnectionType.NONE
    }

    override suspend fun sendData(data: ByteArray): Boolean {
        return connectionUSB?.sendData(data) ?: false
    }

    override suspend fun receiveData(): ByteArray? {
        return connectionUSB?.receiveData()
    }
}