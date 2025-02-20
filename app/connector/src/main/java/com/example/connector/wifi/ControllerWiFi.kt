package com.example.connector.wifi

import com.example.connector.common.BaseController
import com.example.connector.common.ConnectionUtil.ConnectionType

class ControllerWiFi : BaseController() {
    private var connectionWiFi: ConnectionWiFi? = null

    suspend fun connectTo(ipAddress: String, port: Int): Boolean {
        connectionWiFi = ConnectionWiFi(ipAddress, port)
        return startConnection()
    }

    override suspend fun connect(): Boolean {
        return (connectionWiFi?.connect() ?: false)
            .also { isConnected ->
                if (isConnected) connectionType = ConnectionType.WIFI
            }
    }

    override fun disconnect() {
        connectionWiFi?.disconnect()
        connectionType = ConnectionType.NONE
    }

    override suspend fun sendData(data: ByteArray): Boolean {
        return connectionWiFi?.sendData(data) ?: false
    }

    override suspend fun receiveData(): ByteArray? {
        return connectionWiFi?.receiveData()
    }
}