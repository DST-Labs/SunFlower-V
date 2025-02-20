package mavlink

import android.os.Build
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.CommandLong
import io.dronefleet.mavlink.common.MavCmd
import java.io.IOException
import java.util.function.Consumer

class MavlinkDataProcessor(mavlinkConnection: MavlinkConnection) {
    private var mavlinkConnection: MavlinkConnection = mavlinkConnection

    fun startMavlinkMessageListener(onMessageReceived: Consumer<MavlinkMessage<*>?>) {
        Thread {
            while (true) {
                try {
                    val message = mavlinkConnection.next()
                    if (message != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            onMessageReceived.accept(message)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    break
                }
            }
        }.start()
    }

    // Raw IMU 데이터 요청
    fun requestRawImuData(num: Int) {
        val systemId = 255
        val componentId = 0
        val commandLong = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(1)
            .command(MavCmd.MAV_CMD_SET_MESSAGE_INTERVAL)
            .param1(num.toFloat()) // 메시지 ID
            .param2(1000000f) // 1Hz (단위: 1초)
            .confirmation(0)
            .build()

        try {
                mavlinkConnection.send2(systemId, componentId, commandLong)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}