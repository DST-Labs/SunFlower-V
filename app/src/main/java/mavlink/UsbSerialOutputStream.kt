package mavlink

import com.felhr.usbserial.UsbSerialDevice
import java.io.IOException
import java.io.OutputStream


class UsbSerialOutputStream // 생성자
    (
// USB 직렬 장치
    private val device: UsbSerialDevice,
) : OutputStream() {
    @Throws(IOException::class)  // 데이터를 USB 직렬 장치로 쓰기
    override fun write(b: Int) {
        device.write(byteArrayOf(b.toByte()))
    }
}