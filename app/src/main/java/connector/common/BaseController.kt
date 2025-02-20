package com.example.connector.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.connector.common.ConnectionUtil.ConnectionType

abstract class BaseController : ConnectionHandler {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var connectionType: ConnectionType = ConnectionType.NONE

    suspend fun startConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            connect()
        }
    }

    fun terminateConnection() {
        coroutineScope.launch {
            disconnect()

            connectionType = ConnectionType.NONE
        }
    }
}