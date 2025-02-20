package com.example.connector.common

interface ConnectionHandler {
    suspend fun connect(): Boolean
    fun disconnect()
    suspend fun sendData(data: ByteArray): Boolean
    suspend fun receiveData(): ByteArray?
}