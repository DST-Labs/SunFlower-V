package com.example.connector.serial

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.connector.common.ConnectionHandler
import com.example.connector.common.ConnectionUtil.BAUD_RATE
import com.example.connector.common.ConnectionUtil.BUFFER_SIZE
import com.example.connector.common.ConnectionUtil.TIMEOUT
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConnectionSerial(context: Context, private val usbDevice: UsbDevice) : ConnectionHandler {
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbSerialPort: UsbSerialPort? = null

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (availableDrivers.isEmpty()) {
                Log.d("Connector(Serial)", "No USB serial devices found")
                return@withContext false
            }

            val driver = availableDrivers.find { it.device == usbDevice } ?: return@withContext false
            val connection = usbManager.openDevice(driver.device) ?: return@withContext false

            usbSerialPort = driver.ports[0]
            usbSerialPort?.open(connection)
            usbSerialPort?.setParameters(
                BAUD_RATE,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            Log.d("Connector(Serial)", "Connected to USB Serial device")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun disconnect() {
        try {
            usbSerialPort?.close()
            usbSerialPort = null

            Log.d("Connector(Serial)", "Disconnected")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            usbSerialPort?.let {
                it.write(data, TIMEOUT)

                Log.d("Connector(Serial)", "Data sent: ${String(data).trim()}")
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun receiveData(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            usbSerialPort?.let {
                var receivedData: ByteArray? = null
                val bytesRead = it.read(buffer, TIMEOUT)
                if (bytesRead > 0) {
                    receivedData = buffer.copyOf(bytesRead)

                    Log.d("Connector(Serial)", "Data received: ${String(receivedData).trim()}")
                }
                receivedData
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}