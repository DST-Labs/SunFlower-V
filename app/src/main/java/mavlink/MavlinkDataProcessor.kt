/*
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
}*/

package mavlink

import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.CommandLong
import io.dronefleet.mavlink.common.MavCmd
import java.io.IOException


class MavlinkDataProcessor(
    private val mavlinkConnection: MavlinkConnection
) {
    @Volatile
    private var running = false

    private var listenerThread: Thread? = null

    fun startMavlinkMessageListener(
        onMessageReceived: (MavlinkMessage<*>?) -> Unit
    ) {
        if (running) return
        running = true

        listenerThread = Thread {
            while (running) {
                try {
                    val message = mavlinkConnection.next() // 여기서 블로킹
                    onMessageReceived(message)
                } catch (e: IOException) {
                    // 🔴 pipedIn.close() → 여기로 떨어지며 정상 종료
                    break
                }
            }
            running = false
        }.apply {
            name = "MAVLink-Listener"
            start()
        }
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

    /** stop은 "루프 중단 신호"만 담당 */
    fun stop() {
        running = false
        listenerThread?.interrupt()
        listenerThread = null
    }
}




