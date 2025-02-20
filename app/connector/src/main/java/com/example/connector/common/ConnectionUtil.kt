package com.example.connector.common

object ConnectionUtil {
    const val BUFFER_SIZE = 4096
    const val TIMEOUT = 10000
    const val BAUD_RATE = 57600 //115200

    enum class ConnectionType {
        NONE, WIFI, USB, BT, SERIAL
    }
}