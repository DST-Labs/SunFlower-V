package mavlink

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.dstl.sunflower.MainActivity
import java.util.function.Consumer


class UsbHelper (private var activity: Activity, usbPermissionReceiver: BroadcastReceiver? ) {
    val ACTION_USB_PERMISSION: String = "com.example.mavlinktest.USB_PERMISSION"

    // USB 권한 BroadcastReceiver
    private var usbPermissionReceiver: BroadcastReceiver? = usbPermissionReceiver

    // USB 관리자
    private var usbManager: UsbManager? = activity.getSystemService(Context.USB_SERVICE) as UsbManager

    // USB 장치 목록을 보여주는 메서드
    @SuppressLint("MutableImplicitPendingIntent")
    fun showUsbDeviceList(onDeviceSelected: Consumer<UsbDevice?>?) {
        // 연결된 USB 장치들의 목록을 가져옴
        val usbDevices = usbManager!!.deviceList
        val deviceList: MutableList<UsbDevice> = ArrayList()

        // USB 장치들이 있으면
        if (usbDevices.isNotEmpty()) {
            deviceList.addAll(usbDevices.values)

            // 장치 이름 배열 생성
            val deviceNames = arrayOfNulls<String>(deviceList.size)
            for (i in deviceList.indices) {
                val device = deviceList[i]
                deviceNames[i] =
                    device.manufacturerName + " - " + device.productName + " (VID: " + device.vendorId + ", PID: " + device.productId + ")"
            }

            // 다이얼로그로 USB 장치 목록을 보여줌
            val builder = AlertDialog.Builder(
                activity!!
            )
            builder.setTitle("장치 선택")
            builder.setItems(deviceNames) { dialog: DialogInterface?, which: Int ->
                val selectedDevice = deviceList[which]
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                val explicitIntent: Intent = Intent(ACTION_USB_PERMISSION)

                explicitIntent.setPackage(activity.getPackageName());
                // BroadcastReceiver 등록

                // 해당 장치에 대한 권한 요청
                val permissionIntent = PendingIntent.getBroadcast(
                    activity,
                    0,
                    explicitIntent,
                    if (VERSION.SDK_INT >= VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                )

                if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {  // TIRAMISU is Android 13 / API 33
                    ContextCompat.registerReceiver(
                        activity!!,
                        usbPermissionReceiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    ContextCompat.registerReceiver(
                        activity!!,
                        usbPermissionReceiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                }

                usbManager!!.requestPermission(selectedDevice, permissionIntent)
            }
            builder.show()
        }
    }
}