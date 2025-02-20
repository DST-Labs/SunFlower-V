package com.example.connector.serial

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.connector.common.BaseController
import com.example.connector.common.ConnectionUtil

class ControllerSerial(private val context: Context) : BaseController() {
    private var connectionSerial: ConnectionSerial? = null

    suspend fun connectTo(usbDevice: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return if (usbManager.hasPermission(usbDevice)) {
            connectionSerial = ConnectionSerial(context, usbDevice)
            startConnection()
        } else {
            Log.d("Connector(Serial)", "usbDevice has no Permission")
            false
        }
    }

    override suspend fun connect(): Boolean {
        return (connectionSerial?.connect() ?: false)
            .also { isConnected ->
                if (isConnected) connectionType = ConnectionUtil.ConnectionType.SERIAL
            }
    }

    override fun disconnect() {
        connectionSerial?.disconnect()
        connectionType = ConnectionUtil.ConnectionType.NONE
    }

    override suspend fun sendData(data: ByteArray): Boolean {
        return connectionSerial?.sendData(data) ?: false
    }

    override suspend fun receiveData(): ByteArray? {
        return connectionSerial?.receiveData()
    }
}