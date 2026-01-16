package com.dstl.sunflower

import CompassIcon
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.location.Location
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import com.dstl.sunflower.databinding.ActivityMainBinding
import com.example.connector.bluetooth.ControllerBT
import com.example.connector.serial.ControllerSerial
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.OnSuccessListener
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.GpsRawInt
import io.dronefleet.mavlink.common.RawImu
import io.dronefleet.mavlink.minimal.Heartbeat
import kotlinx.coroutines.Runnable
import mavlink.MavlinkDataProcessor
import mavlink.UsbHelper
import mavlink.UsbSerialOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp

class MainActivity : AppCompatActivity(), OnMarkerDragListener, OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding // MainActivity의 binding 선언

    //private val controllerUSB = ControllerUSB() // USB controller 선언
    private val controllerBT = ControllerBT() // bluetooth controller 선언
    private val controllerSerial = ControllerSerial(this) // Serial controller 선언

    // USB 선언
    private var usbDevice: UsbDevice? = null // USB 디바이스 목록
    private var usbManager : UsbManager? = null // USB 디바이스 메니저
    private var usbHelper: UsbHelper? = null // USB 디바이스 헬퍼
    //private var usbdevicename : String? = null // USB 디바이스 이름
    private val ACTION_USB_PERMISSION = "com.example.mavlinktest.USB_PERMISSION"
    private val USBBaudrate = 57600 // USB 텔레메트리 통신 속도
    private var mavlinkDataProcessor : MavlinkDataProcessor? = null // Mavlink 데이터 프로세스 (통신 프로토콜 라이브러리)

    private var btn_usb_status = 0
    enum class UsbState {
        DISCONNECTED,
        WAITING,
        CONNECTED
    }
    @Volatile
    private var usbState = UsbState.DISCONNECTED
    @Volatile
    private var usbConnection: UsbDeviceConnection? = null
    private var serialDevice: UsbSerialDevice? = null

    private var pipedOut: PipedOutputStream? = null
    private var pipedIn: PipedInputStream? = null


    // Bluetooth 선언
    var BluetoothMessage : String = "Bluetooth Message"
    private var bluetoothAdapter: BluetoothAdapter? = null // 블루투스 어댑터
    private var bluetoothDevice: BluetoothDevice? = null // 블루투스 디바이스 목록
    private var devices: Set<BluetoothDevice>? = null // 블루투스 디바이스 데이터 셋
    private var bluetoothSocket: BluetoothSocket? = null // 블루투스 소켓
    var pariedDeviceCount: Int = 0 // 블루투스 페어링 디바이스 크기

    private var outputStream: OutputStream? = null // 블루투스에 데이터를 출력하기 위한 출력 스트림
    private var inputStream: InputStream? = null // 블루투스에 데이터를 입력하기 위한 입력 스트림

    private var handler = Handler(Looper.getMainLooper()) // 블루투스 메세지용 핸들러

    private var btn_bt_status = 0

    // 수신 스레드 제어용
    private var listenThread: Thread? = null
    @Volatile
    private var isListening: Boolean = false


    companion object {
        // SPP 기본 UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private var logWriter: FileWriter? = null
    }

    // AAT 통신 변수 선언
    private val App_ID: ByteArray =
        byteArrayOf(0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte()) // AAT ID
    private var AAT_ID: ByteArray =
        byteArrayOf(0xD0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte()) // APP ID
    // AAT_CMD_REQ 정의
    private val CMD_Command_SYNC: ByteArray =
        byteArrayOf(0x00.toByte(), 0x00.toByte())

    private val CMD_Command_AAT_Init: ByteArray =
        byteArrayOf(0x0A.toByte(), 0x01.toByte())

    private val CMD_Command_AAT_Restart: ByteArray =
        byteArrayOf(0x0A.toByte(), 0x02.toByte())

    private val CMD_Command_Upright_the_antenna_pod: ByteArray =
        byteArrayOf(0xC0.toByte(), 0x01.toByte())

    private val CMD_Command_Down_the_Antenna_pod_horizotal: ByteArray =
        byteArrayOf(0xC0.toByte(), 0x02.toByte()) // AAT Inti test

    private val CMD_Command_Send_current_yaw: ByteArray =
        byteArrayOf(0xC1.toByte(), 0x00.toByte()) // AAT Inti test

    private val CMD_Command_send_current_tilt: ByteArray =
        byteArrayOf(0xC1.toByte(), 0x01.toByte()) // AAT Inti test

    private val CMD_Command_Radio_ON: ByteArray =
        byteArrayOf(0xD0.toByte(), 0x00.toByte()) // AAT Inti test

    private val CMD_Command_Send_radio_signal_strength: ByteArray =
        byteArrayOf(0xD0.toByte(), 0x01.toByte()) // AAT Inti test

    private val CMD_Command_Stop_radio: ByteArray =
        byteArrayOf(0xD0.toByte(), 0x02.toByte()) // AAT Inti test


    // map 변수 선언

    lateinit var providerClient: FusedLocationProviderClient
    lateinit var apiClient: GoogleApiClient
    var googleMap: GoogleMap? = null
    var custommapzoom : Float = 15f
    var custommapzoom_scale : Float = 1.0f

    // GPS 관련 변수 선언
    var AATlat = 37.488006 // AAT lat
    var AATlong = 127.008915 // AAT long
    var AATalt = 30.0 // AAT alt
    var AATYaw = 0.0
    var AATrssi = 0

    val PI = 3.14159265358979323846 // 안테나 트래커 방향 전환용 변수

    var dronelat = 0.0 // Drone lat
    var dronelong = 0.0 // Drone long
    var dronealt = 30.0 // Drone alt
    var markerDrone : Marker? = null // 드론 이미지
    var markeranternna : Marker? = null // AAT 이미지
    var markerangle : Marker? = null // 수동 조정 이미지


    // Test 비행 시뮬레이션 관련 변수 선언
    var testOnoff : Boolean = false // Google Map 테스트 용 마커 클릭 가능 여부

    private var autoflyhandler = Handler(Looper.getMainLooper()) // App to AAT 메시지 헨들러
    var autoflystatusUpdateTask: Runnable? = null // App to AAT 메시지 헨들러 럼블
    var Autoflyis = false // 테스트용 임무 비행 실행 여부 판단 < 테스트 비행중 중간 멈춤으로 사용

    private val markerList = mutableListOf<Marker>() // 선택된 테스트용 마커 리스트
    private val automarkerList = mutableListOf<LatLng>() // markerList 기반으로 생성된 비행 마커 리스트
    private var polyline: Polyline? = null // 마커간 선 그리기

    private var Testdronespeed : Int = 100 // 비행 속도 설정 단위 :  m/s
    private var Testradius : Double = 1500.0 // 비행 원형 반지름
    private var TestradiusPoint :Int = 12 // 비행 포인트 개수 12
    var TestAppalt : Double = 100.0
    var testag : Int = 0 // 원형 비행 시작 포인트 지정
    var testcricleSW : Boolean = true // 원형 비행의 시계 반시계 방행 선택 true : 시계방향 / fales : 반시계 방향
    var CMD_REQ_SW : Boolean = false // CMD REQ 프로토콜 실행 유무
    var testinfinity : Boolean = true
    var BT_connect_Set : Boolean = false // Bluetooth 연결 상태 확인
    @Volatile
    var RF_connect_Set : Boolean = false // USB 연결 상태 확인

    private var dronepolyline: Polyline? = null // markerList 간 라인 설정
    private var aatpolyline: Polyline? = null

    var CountSendDroneLOC_IND : Int = 0 // 비행 시뮬레이터 개수 확인용 변수 > 마커 개수
    var CountSendDroneLOC_gps : Int = 0 // 비행 시뮬레이터 개수 확인용 변수 > GPS 개수

    val dronelogv : String = "Drone Protocol Log" // 비행 시뮬레이터 로그 용 tag

    // 화면 Log 창 관련 변수 선언
    private lateinit var magframeLayout: FrameLayout // 화면 로그창 프레임 레이아웃
    private var initialX = 0f // 로그창 화면절대 좌표 X
    private var initialY = 0f // 로그창 화면절대 좌표 Y
    private var initialWidth = 0 // 로그창 화면 넓이
    private var initialHeight = 0 // 로그창 화면 깊이

    // Map 센터 이동 관련 변수

    var aat_center_is = false
    var drone_center_is = false

    // tester 모드 활성화
    var testercount = 0
    var testermodeon = false

    // marker

    private lateinit var popup1: TextView
    private lateinit var popup2: TextView
    private var isFirstMapMove = true

    @Volatile
    private var mapMarkerEnabled = true
    @Volatile
    private var mapMarkerRunning = false

    private var mapMarkerThread: Thread? = null

    // V&V test
    var drone_data_true_count = 0
    var drone_data_fail_count = 0
    var aat_data_true_count = 0
    var aat_data_fail_count = 0

    var aat_IND_time : Long = 0
    var aat_REQ_time : Long = 0


    val Logchange = false // 0 : normal , 1 : V&V

    // manual angle
    var isManualAngle = false
    var isManualAngle_touch = false
    var ManualAngle = 0

    // Log tample

    private val BluetoothLog : String = "BluetoothLog"
    private val DroneLog : String = "DroneLog"
    private val ApplcationLog : String = "ApplcationLog"
    private val AATLog : String = "AATLog"

    // lifecycle
    override fun onStart() {
        super.onStart()
        // Bluetooth 리시버 활성화
        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(bluetoothReceiver, bluetoothFilter)
        registerReceiver(usbAttachDetachReceiver, bluetoothFilter)
    }

    override fun onStop() {
        super.onStop()
        // 앱 종료시 AAT에 StopREQ 전송
        if(bluetoothSocket?.isConnected == true){
            sendStopREQ()
        }
        stopMapMarkerThread()
        stopUsbCommunication()
        disconnectBluetooth()
        unregisterReceiver(bluetoothReceiver)
        unregisterReceiver(usbPermissionReceiver)
        unregisterReceiver(usbAttachDetachReceiver)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MainActivity xml 바인딩 및 선언
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        enableEdgeToEdge()

        totallog(ApplcationLog,"MainActivity Start!",true,false,true,false)

        val token = getSharedPreferences("auth", MODE_PRIVATE)
            .getString("token", null)

        if (token == null) {
            val i = Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        (supportFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment).getMapAsync(this)

        // USB 메니저 선언
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        usbHelper = UsbHelper(this, usbPermissionReceiver)

        val cv = findViewById<ComposeView>(R.id.compassCompose)

        cv.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )

        cv.setContent {
            MaterialTheme {
                CompassIcon(
                    iconSize = 100.dp,
                    onClick = {
                        // TODO: 클릭 시 상세 나침반 UI 열기(다이얼로그/BottomSheet 등)
                    }
                )
            }
        }

        binding.mainVerstionTv.text = "SunFlower V1.0 Copyright (C) 2025 DSTL All Rights Reserved."

        // 버튼 이벤트

        /*
        RF 연결 방법
         1) 앱 실행 후 USB 연결
         2) 테스트용 RF 기준 CP2102 USB 엑세스 허용을 확인 누름
         3) 이후 RFConnect 버튼 눌러 실행
         */

        // RF USB 실행및 활성화

        // 드론 연결 버튼
        binding.btnDroneConStatus.setOnClickListener{
            if(btn_usb_status == 0) {
                usbHelper!!.showUsbDeviceList { device: UsbDevice? ->
                    this.onDeviceSelected(
                        device!!
                    )
                    //totallog(DroneLog,"Drone Connect",true,true,false,false)
                }
            }
            else if(btn_usb_status == 1) {

            }
            else if(btn_usb_status == 2) {
                if(drone_center_is) {
                    binding.btnDroneConStatus.background = null
                }
                else {
                    binding.btnDroneConStatus.background = ContextCompat.getDrawable(this, R.drawable.circle_border)
                    binding.btnAatConStatus.background = null
                    aat_center_is = false
                }
                drone_center_is = !drone_center_is
            }
        }


        /*
        AAT (Bluetooth) 연결 방법
         1) 앱 실행 후 BTconnect 실행
         2) 연결하고자 하는 블루투스 항목 선택
         3) Toast 통해 연결 디바이스의 MAC 확인
         */

        // 비행 시뮬레이터 실행 버튼 숨김
        binding.btnTeststart.isInvisible = true
        binding.btnTestReset.isInvisible = true
        binding.btnTestcancel.isInvisible = true
        binding.tvLog.movementMethod = ScrollingMovementMethod()

        binding.autostartBT.isInvisible = true
        binding.aatCmdReqBT.isInvisible = true
        binding.frmlMassagebox.isInvisible = true

        // 안테나 트래커 연결 버튼
        binding.btnAatConStatus.setOnClickListener{
            if(btn_bt_status == 0) {
                if (bluetoothAdapter!!.isEnabled) { // 블루투스가 활성화 상태 (기기에 블루투스가 켜져있음)
                    selectBluetoothDevice() // 블루투스 디바이스 선택 함수 호출
                }
                else {
                    totallog(AATLog,"블루투스를 활성화 해주세요",true,false,true,false)
                    //Toast.makeText(this, "블루투스를 활성화 해주세요", Toast.LENGTH_SHORT).show()
                }
            }
            else if(btn_bt_status == 1) {

            }
            else if(btn_bt_status == 2) {
                if(aat_center_is) {
                    binding.btnAatConStatus.background = null
                }
                else {
                    binding.btnAatConStatus.background = ContextCompat.getDrawable(this, R.drawable.circle_border)
                    binding.btnDroneConStatus.background = null
                    drone_center_is = false
                    drone_center_is = false
                }
                aat_center_is = !aat_center_is
            }
        }

        // 로고 선택 버튼
        binding.btnLogo.setOnClickListener {
            testercount ++
            if (testercount == 5) {
                testermodeon = !testermodeon
                testermodeon(testermodeon)
                testercount = 0
            }
        }

        /*
        AAT Test 용 Auto Fly 버튼
         */
        // 비행 시뮬레이터 다이얼로그 박스 이벤트 실행
        binding.autostartBT.setOnClickListener {
            val testdialog = TestCustomDialog(this, Testdronespeed,TestAppalt,Testradius,TestradiusPoint) {
                spped, alt, circleradius, circlepoint, circlestart, spin, infinity, btn ->
                Testdronespeed = spped // 비행 스피드 값
                TestAppalt = alt
                Testradius = circleradius // 비행 시뮬레이터 원의 크기 (반지름)
                TestradiusPoint = circlepoint // 비행 시뮬레이터 원의 마커 개수 (하나의 원에 몇개의 마커로 구성 할지 결정)
                testag = circlestart // 비행 시뮬레이터 원의 시작 좌표
                testcricleSW = spin // 비행 시뮬레이터 원의 회전 방향( 시계 방향 / 반시계 방향)
                testinfinity = infinity // 비행 시뮬레이터 무한 동작
                //val testmessage = Testdronespeed.toString() + " / " + TestradiusPoint.toString()  + " / " + testag.toString() + " / " + testcricleSW.toString()
                if(btn){ // 원형 버튼 선택
                    //Toast.makeText(this,testmessage + " / circlebtn", Toast.LENGTH_SHORT).show()
                    var circlecenter = LatLng(AATlat,AATlong)
                    generateCirclePoints(circlecenter,Testradius,TestradiusPoint,testcricleSW)
                    testOnoff = true
                    binding.btnTeststart.isInvisible = false
                    binding.btnTestReset.isInvisible = false
                    binding.btnTestcancel.isInvisible = false
                }
                else{ // 커스텀 마커 선택
                    //Toast.makeText(this, testmessage + " / custombtn", Toast.LENGTH_SHORT).show()
                    testOnoff = true
                    binding.btnTeststart.isInvisible = false
                    binding.btnTestReset.isInvisible = false
                    binding.btnTestcancel.isInvisible = false
                }
            }
            testdialog.show()

        }

        // 비행 시뮬레이터 시작 버튼
        binding.btnTeststart.setOnClickListener {
            Autofly()
            testOnoff = false
            binding.btnTeststart.isEnabled = false
        }

        // 비행 시뮬레이터 초기화 버튼
        binding.btnTestReset.setOnClickListener{
            Autoflyreset()
            binding.btnTeststart.isEnabled = true
        }

        // 비행 시뮬레이터 종료 버튼
        binding.btnTestcancel.setOnClickListener {
            Autoflyreset()
            binding.btnTeststart.isEnabled = true
            binding.btnTeststart.isInvisible = true
            binding.btnTestReset.isInvisible = true
            binding.btnTestcancel.isInvisible = true
        }

        binding.btnAatManualAngle.setOnClickListener {
            if(BT_connect_Set) {
                isManualAngle = !isManualAngle
                if(isManualAngle){
                    change_btn_con_manual_icon(1)
                    manualanglemarker(AATlat,AATlong,1)
                    isManualAngle_touch = true
                    totallog(AATLog,"안테나 수동 조작 실행",true,false,true,true)
                }
                else {
                    change_btn_con_manual_icon(0)
                    removemanualanglemarker()
                    isManualAngle_touch = false
                    Send_Drone_LOC_IND(dronelat, dronelong, dronealt)
                    totallog(AATLog,"안테나 수동 조작 종료",true,false,true,true)
                }
            }
            else {
                totallog(AATLog,"안테나 트래커를 연결하셔야 사용 가능합니다.",true,false,true,true)
                //Toast.makeText(this, " NO Connect AAT !!", Toast.LENGTH_SHORT).show()
            }
        }

        //AAT CMD REQ SEND 버튼
        binding.aatCmdReqBT.setOnClickListener{
            val testcmddialog = TestCmdreqDialog(this) {
                    index ->
                //Toast.makeText(this, " CMDREQ SELETE BTN : " + index.toString(), Toast.LENGTH_SHORT).show()
                if(isManualAngle){
                    when (index) {
                        1 -> sendCMDREQ("SYNC",CMD_Command_SYNC)
                        2 -> sendCMDREQ("AAT_Init",CMD_Command_AAT_Init)
                        3 -> sendCMDREQ("AAT_Restart",CMD_Command_AAT_Restart)
                        4 -> {
                            showNumberInputDialog(
                                title = "Yaw 값 입력",
                                message = "0 ~ 359 사이의 각도를 입력하세요."
                            ) { value ->
                                // TODO: value를 실제 프로토콜에 맞게 사용
                                // 예시: sendCMDREQ("Make_yaw", makeYawCommand(value))
                                //sendCMDREQ("Make_yaw", CMD_Command_Make_yaw(value))
                                Send_AAT_CMD_REQ_Manual_Angle(value)
                                AATYaw = value.toDouble()
                            }
                        }
                        5 -> {
                            showNumberInputDialog(
                                title = "Tilt 값 입력",
                                message = "0 ~ 90 사이의 각도를 입력하세요."
                            ) { value ->
                                // TODO: value를 실제 프로토콜에 맞게 사용
                                Send_AAT_CMD_REQ_Manual_Angle_tilt(value)
                            }
                        }
                        6 -> sendCMDREQ("Upright_the_antenna_pod",CMD_Command_Upright_the_antenna_pod)
                        7 -> sendCMDREQ("Down_the_Antenna_pod_horizotal",CMD_Command_Down_the_Antenna_pod_horizotal)
                        8 -> sendCMDREQ("Send_current_yaw",CMD_Command_Send_current_yaw)
                        9 -> sendCMDREQ("Send_current_tilt",CMD_Command_send_current_tilt)
                        10 -> sendCMDREQ("Radio_ON",CMD_Command_Radio_ON)
                        11 -> sendCMDREQ("Send_radio_signal_strength",CMD_Command_Send_radio_signal_strength)
                        12 -> sendCMDREQ("Stop_radio",CMD_Command_Stop_radio)
                    }
                }
                else {
                    totallog(AATLog,"안테나 트래커를 연결하셔야 사용 가능합니다.",true,false,true,true)
                    //Toast.makeText(this, "NO Connect AAT !!", Toast.LENGTH_SHORT).show()
                }

            }
            testcmddialog.show()
        }

        requestBluetoothPermissions()

        // 로그 화면 창 조절 장치
        magframeLayout = findViewById(R.id.frml_massagebox)
        magframeLayout.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 터치 시작 지점과 초기 크기 저장
                    initialX = event.rawX
                    initialY = event.rawY
                    initialWidth = view.width
                    initialHeight = view.height
                }
                MotionEvent.ACTION_MOVE -> {
                    // 터치 이동 거리 계산
                    val dx = initialX - event.rawX
                    val dy = initialY - event.rawY

                    // 새로운 크기 설정 (너무 작아지지 않도록 최소 크기 지정)
                    val newWidth = (initialWidth + dx).toInt().coerceAtLeast(100)
                    val newHeight = (initialHeight + dy).toInt().coerceAtLeast(100)

                    // 레이아웃 크기 변경
                    val layoutParams = view.layoutParams
                    layoutParams.width = newWidth
                    layoutParams.height = newHeight
                    view.layoutParams = layoutParams
                }
            }
            true
        }

        // google Map 선언
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (it.all { permission -> permission.value == true }) {
                apiClient.connect()
            } else {
                totallog(ApplcationLog,"Google Map 권한 거부..",true,false,true,false)
                //Toast.makeText(this, "권한 거부..", Toast.LENGTH_SHORT).show()
            }
        }

        providerClient = LocationServices.getFusedLocationProviderClient(this)
        apiClient = GoogleApiClient.Builder(this)
            .addApi(LocationServices.API)
            .build()

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !== PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            )
        } else {
            apiClient.connect()
        }

        popup1 = findViewById(R.id.popup1)
        popup2 = findViewById(R.id.popup2)


    }

    override fun onDestroy() {
        super.onDestroy()
        totallog(ApplcationLog,"MainActivity Destroy!",true,false,true,false)
    }

    // 최초 앱 실행시 Map 관련 초기화 작업 진행
    fun onConnected(p0: Bundle?) {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) === PackageManager.PERMISSION_GRANTED){
            providerClient.lastLocation.addOnSuccessListener(
                this@MainActivity,
                object : OnSuccessListener<Location> {
                    override fun onSuccess(p0: Location?) {
                        p0?.let {
                            // 최초 실행시 더미 좌표로 중앙 이동
                            //moveATTcenterMap(startlat,startlong)
                        }
                    }
                }
            )
            apiClient.disconnect()
        }
    }

    override fun onMarkerDrag(p0: Marker) {
        TODO("Not yet implemented")
    }

    // 드론 이미지 드롭시 드론 이미지 위치 변경 및 AAT 좌표 전달 > 드론 테스트용 코드
    override fun onMarkerDragEnd(marker: Marker) {
        //googleMap?.clear()
        movemarker(marker.position.latitude,marker.position.longitude,TestAppalt,AATlat,AATlong,AATalt)
    }

    override fun onMarkerDragStart(p0: Marker) {
        TODO("Not yet implemented")
    }

    // Google Map 기본 인터페이스
    fun onConnectionFailed(p0: ConnectionResult) {
    }

    private fun handleMapTap(latLng: LatLng) {
        if(testOnoff){
            addMarker(latLng)
        }
        else if(isManualAngle){
            var angle = bearingBetweenPoints(AATlat,AATlong,latLng.latitude,latLng.longitude)
            if(isManualAngle_touch) {
                //totallog(DroneLog,"sendStartREQ / "+"LATLOG : " + latLng.latitude + "," + latLng.longitude + ", angle : " + testangle,true,true,false,true)
                Send_AAT_CMD_REQ_Manual_Angle(angle)
                ManualAngle = angle
            }
        }
    }


    // Google Map 동작 완료시 구글 맵 선언
    override fun onMapReady(p0: GoogleMap) {
        googleMap = p0
        with(googleMap) {
            this?.setOnMarkerDragListener(this@MainActivity)
        }

        googleMap!!.uiSettings.isCompassEnabled = true


        googleMap!!.setOnMapClickListener { latLng ->
            handleMapTap(latLng)
        }

        googleMap!!.setOnCameraIdleListener {
            custommapzoom = googleMap!!.cameraPosition.zoom
            custommapzoom_scale = when {
                custommapzoom > 18 -> 0.75f
                custommapzoom > 16 -> 0.73f
                custommapzoom > 14 -> 0.71f
                custommapzoom > 12 -> 0.67f
                custommapzoom > 10 -> 0.66f
                else -> 0.65f
            }
        }

        popup1.visibility = View.INVISIBLE
        popup2.visibility = View.INVISIBLE

        googleMap!!.setOnMarkerClickListener { marker ->

            val tag = (marker.tag as? String)?.trim().orEmpty()

            if(testOnoff){
                removeMarker(marker)
            }
            when (tag) {
                "manualanlge" -> {
                    handleMapTap(marker.position)
                    true
                }
                "markerDrone" -> {
                    popup1.visibility = if (popup1.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    true
                }
                "markeranternna" -> {
                    popup2.visibility = if (popup2.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    true
                }
                else -> {
                    true
                }
            }

        }

    }

    private fun testermodeon(testeris : Boolean) {
        if(testeris) {
            binding.autostartBT.isInvisible = false
            binding.aatCmdReqBT.isInvisible = false
            binding.frmlMassagebox.isInvisible = false
        }
        else {
            binding.autostartBT.isInvisible = true
            binding.aatCmdReqBT.isInvisible = true
            binding.frmlMassagebox.isInvisible = true
        }
    }

    // btn_aat 이미지 변경
    private fun change_btn_con_aat_icon(status: Int) {
        if(status == 0){
            binding.btnAatConStatus.setImageResource(R.drawable.img_aat_red)
            btn_bt_status = 0
            binding.btnAatConStatus.background = null
        }
        else if(status == 1){
            binding.btnAatConStatus.setImageResource(R.drawable.img_aat_yellow)
            btn_bt_status = 1
        }
        else if(status == 2){
            binding.btnAatConStatus.setImageResource(R.drawable.img_aat_green)
            btn_bt_status = 2
        }
    }

    private fun change_btn_con_drone_icon(status: Int) {
        if(status == 0){
            binding.btnDroneConStatus.setImageResource(R.drawable.img_drone_red)
            binding.btnDroneConStatus.background = null
            btn_usb_status = 0
        }
        else if(status == 1){
            binding.btnDroneConStatus.setImageResource(R.drawable.img_drone_yellow)
            btn_usb_status = 1
        }
        else if(status == 2){
            binding.btnDroneConStatus.setImageResource(R.drawable.img_drone_green)
            btn_usb_status = 2
        }
    }

    private fun change_btn_con_manual_icon(status: Int) {
        if(status == 0){
            binding.btnAatManualAngle.setImageResource(R.drawable.img_aat_manualangle_red)
        }
        else if(status == 1){
            binding.btnAatManualAngle.setImageResource(R.drawable.img_aat_manualangle_green)
        }
    }

    // 마커 생성
    private fun addMarker(position: LatLng) {
        val markerNumber = markerList.size + 1 // 리스트 순서대로 번호 부여
        val marker = googleMap!!.addMarker(
            MarkerOptions()
                .position(position)
                .icon(getMarkerIconWithNumber(markerNumber))
        )
        marker?.let {
            markerList.add(it)
        }
        drawPolyline()
    }

    // 삭제시 마커 번호 초기화
    private fun redrawMarkers() {
        googleMap?.clear() // 지도 초기화
        val tempList = mutableListOf<Marker>()

        for ((index, marker) in markerList.withIndex()) {
            val newMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(marker.position)
                    .icon(getMarkerIconWithNumber(index+1))
            )
            newMarker?.let { tempList.add(it) }
        }
        markerList.clear()
        markerList.addAll(tempList) // 리스트 업데이트
    }

    // 마커 번호 생성
    private fun getMarkerIconWithNumber(number: Int): BitmapDescriptor {
        // 숫자를 포함할 텍스트를 그리기 위한 Bitmap 생성
        val text = "$number"
        val paint = Paint()
        paint.color = 0xFF000000.toInt() // 텍스트 색상 (검정)
        paint.textSize = spToPx(16f) // 텍스트 크기
        paint.isAntiAlias = true
        paint.typeface = Typeface.DEFAULT_BOLD

        // 텍스트 크기 계산
        val width = paint.measureText(text).toInt()
        val height = paint.textSize.toInt()

        // 텍스트가 포함된 비트맵 생성
        val bitmap = Bitmap.createBitmap(width + 20, height + 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFF00.toInt()) // 배경색 (노랑)
        canvas.drawText(text, 10f, height.toFloat(), paint)

        // 비트맵을 마커 아이콘으로 반환
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // 마커 삭제
    private fun removeMarker(marker: Marker) {
        marker.remove() // 지도에서 제거
        markerList.remove(marker) // 리스트에서 제거
        redrawMarkers()
        drawPolyline()
    }

    // 마커 간 선 연결
    private fun drawPolyline() {
        polyline?.remove() // 기존 선 제거

        if (markerList.size < 2) return // 마커가 2개 이상 있어야 선을 그림

        val polylineOptions = PolylineOptions().apply {
            color(0xFF0000FF.toInt()) // 선 색상 (파란색)
            width(8f) // 선 두께
            for (marker in markerList) {
                add(marker.position) // 마커 위치 추가
            }
        }

        polyline = googleMap?.addPolyline(polylineOptions) // 지도에 선 추가
    }


    // 비행 시뮬레이터 실행
    private fun Autofly() {
        totallog("Autofly","Autofly 리스트 실행",true,false,false,false)
        var startpoint : LatLng? = null
        var endpoint : LatLng? = null
        automarkerList.clear()
         markerList.forEachIndexed { index, marker ->
            //Log.d("MarkerList", "${index + 1}: (${marker.position.latitude}, ${marker.position.longitude})")
            if(index == 0) {
                startpoint = LatLng(marker.position.latitude, marker.position.longitude)
                automarkerList.add(startpoint!!)
            }
            else {
                endpoint = LatLng(marker.position.latitude, marker.position.longitude)
                val pointnum = calculatepointnum(getDistanceMeterBetweenTwoLatLngCoordinate(startpoint!!,endpoint!!).toInt(),Testdronespeed)
                interpolatePoints(startpoint!!,endpoint!!,pointnum)
                automarkerList.add(endpoint!!)
                startpoint = endpoint
            }
        }
        Autoflyis = true
/*        Log.d(
            ContentValues.TAG,
            "Sent DroneLOC_IND / create automarkerList size  : " + automarkerList.size
        )*/
        autoflystatusUpdateTask = Runnable {autoflymatker()}
        autoflyhandler.post(autoflystatusUpdateTask!!)
    }

    // 비행 시뮬레이터 초기화
    private fun Autoflyreset() {
        googleMap!!.clear()
        markeranternna = null
        markerDrone = null
        popup1.visibility = View.INVISIBLE
        popup2.visibility = View.INVISIBLE
        markerList.clear()
        Autoflyis = false
        automarkerList.clear()
        if(autoflystatusUpdateTask != null){
            autoflyhandler.removeCallbacks(autoflystatusUpdateTask!!)
        }
        CountSendDroneLOC_IND = 0
        CountSendDroneLOC_gps = 0
    }

    // 비행 시뮬레이터용 핸들러 > 비행중 AAT에 좌표값 전달
    private fun autoflymatker(){
        CountSendDroneLOC_IND = 0
        thread(start = true){
            run breaker@{
                var shouldRunOnceAfterFalse = false
                while (true) {
                    if (testinfinity) {
                        if (!shouldRunOnceAfterFalse) {
                            shouldRunOnceAfterFalse = true
                        } else {
                            return@breaker
                        }
                    }
                    automarkerList.forEachIndexed { index, latLng ->
                        sleep(1000)
                        if (Autoflyis) {
                            runOnUiThread {
                                if (!dronelat.equals(latLng.latitude)) {
                                    ++CountSendDroneLOC_gps
                                }
                                dronelat = latLng.latitude
                                dronelong = latLng.longitude
                                dronealt = TestAppalt
                                updatedroneLogview(
                                    dronelat.toString(),
                                    dronelong.toString(),
                                    dronealt.toString()
                                )
                                movemarker(
                                    dronelat,
                                    dronelong,
                                    dronealt,
                                    AATlat,
                                    AATlong,
                                    AATalt
                                )
                                if (index == automarkerList.size - 1) {
                                    CountSendDroneLOC_IND = 0
                                    CountSendDroneLOC_gps = 0
                                    if(testinfinity){
                                        binding.btnTeststart.isEnabled = true
                                    }
                                    else{
                                        binding.btnTeststart.isEnabled = false
                                    }

                                }
                            }
                        } else {
                            return@breaker
                        }
                    }
                }
            }
        }
    }

    // 드론 원형 비행 마커 생성
    fun interpolatePoints(start: LatLng, end: LatLng, numPoints: Int): List<LatLng> {
        val points = mutableListOf<LatLng>()

        for (i in 1..numPoints) {
            val fraction = i.toDouble() / (numPoints + 1)
            val lat = (end.latitude - start.latitude) * fraction + start.latitude
            val lng = (end.longitude - start.longitude) * fraction + start.longitude
            points.add(LatLng(lat, lng))
            automarkerList.add(LatLng(lat, lng))
            //Log.d("MarkerList", "MID : " + "${i}: (${lat}, ${lng})")
        }

        return points
    }

    // Map fun

    // 드론 이미지 표시에 대한 함수
    private fun getBitmapDescriptorFactoryDrone(resId: Int, scale : Float ): Bitmap? {
        var bitmap : Bitmap? = null
        val drawable = ResourcesCompat.getDrawable(resources, resId, null)
        if(drawable != null){
            bitmap = Bitmap.createBitmap(drawable.intrinsicWidth,drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0,0,canvas.width,canvas.height)
            drawable.draw(canvas)
        }
        val width = (bitmap!!.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    // AAT 이미지 표시에 대한 함수
    private fun getBitmapDescriptorFactoryAnternna(resId: Int, anternnadeg: Float, scale : Float): Bitmap? {
        var bitmap : Bitmap? = null
        val drawable = ResourcesCompat.getDrawable(resources, resId, null)
        if(drawable != null){
            bitmap = Bitmap.createBitmap(drawable.intrinsicWidth,drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0,0,canvas.width,canvas.height)
            drawable.draw(canvas)
        }
        val width = (bitmap!!.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    // Map Zoom Level에 따른 거리 조정
    fun getLengthFromZoom(zoomLevel: Float, latitude: Double, desiredPixelLength: Float): Double {
        val metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(latitude)) / Math.pow(2.0, zoomLevel.toDouble())
        val meterLength = metersPerPixel * desiredPixelLength

        val earthRadius = 6378137.0 // meter
        return (meterLength / earthRadius) * (180 / Math.PI) // 도(degree) 단위 거리로 변환
    }

    // 해당 좌표로 Map 중앙점 이동
    private fun moveCenterMap(latitude: Double, longitude: Double){
        val latLng = LatLng(latitude, longitude)

        val zoomLevel = if (isFirstMapMove) {
            isFirstMapMove = false
            16f
        } else {
            custommapzoom
        }

        val position : CameraPosition = CameraPosition.Builder()
            .target(latLng)
            .zoom(zoomLevel)
            .bearing(0f)
            .build() // 지도 중심 위치 이동
        googleMap!!.moveCamera(CameraUpdateFactory.newCameraPosition(position)) // 해당 좌표로 맵 이동
    }

    private fun manualanglemarker(aatlatitude: Double, aatlongitude: Double, changeicon : Int) {

        val aatlatLng = LatLng(aatlatitude, aatlongitude)

        if (isManualAngle){
            if(markerangle == null) {
                markerangle = googleMap?.addMarker(
                    MarkerOptions()
                        .icon(getBitmapDescriptorFactoryDrone(R.drawable.img_screen_manualangle_green,custommapzoom_scale)?.let {
                            BitmapDescriptorFactory.fromBitmap(
                                it
                            )
                        })
                        .position(aatlatLng)
                        .anchor(0.5F,0.5F)
                )
                markerangle?.tag = "manualanlge"
            }
            else {
                if (changeicon == 0) {
                    markerangle!!.setIcon(getBitmapDescriptorFactoryDrone(R.drawable.img_screen_manualangle_red,custommapzoom_scale)?.let {
                        BitmapDescriptorFactory.fromBitmap(
                            it
                        )
                    })
                }
                else if (changeicon == 1) {
                    markerangle!!.setIcon(getBitmapDescriptorFactoryDrone(R.drawable.img_screen_manualangle_green,custommapzoom_scale)?.let {
                        BitmapDescriptorFactory.fromBitmap(
                            it
                        )
                    })
                }
            }

        }
    }

    private fun removemanualanglemarker() {
        markerangle!!.remove()
        markerangle = null
    }

    fun runMapmarkerUi(action: () -> Unit) {
        if (!mapMarkerEnabled) return
        runOnUiThread {
            if (mapMarkerEnabled) {
                action()
            }
        }
    }

    fun startMapMarkerThread() {
        // 이미 실행 중이면 무시
        if (mapMarkerRunning) {
            //totallog("Autofly","이미 스레드가 실행 중입니다",true,false,false,false)
            //Log.d("MapMarker", "이미 스레드가 실행 중입니다.")
            return
        }

        mapMarkerRunning = true

        mapMarkerThread = Thread {
            //totallog("Autofly","Autofly 스레드 시작",true,false,false,false)
            //Log.d("MapMarker", "스레드 시작")

            try {
                while (mapMarkerRunning && !Thread.currentThread().isInterrupted) {

                    // 🔹 실제 작업
                    Thread.sleep(500)
                    runMapmarkerUi {
                        // 예: 지도 마커 업데이트 같은 UI 작업
                        movemarker(dronelat,dronelong,dronealt,AATlat,AATlong,AATalt)
                    }
                }
            } catch (e: InterruptedException) {
                //totallog("Autofly","스레드 인터럽트됨",true,false,false,false)
                //Log.d("MapMarker", "스레드 인터럽트됨")
            } finally {
                mapMarkerRunning = false
                //totallog("Autofly","스레드 종료",true,false,false,false)
                //Log.d("MapMarker", "스레드 종료")
            }
        }

        mapMarkerThread?.start()
    }

    fun stopMapMarkerThread() {
        if (!mapMarkerRunning) {
            totallog(ApplcationLog,"실행 중인 스레드 없음",true,false,false,false)
            //Log.d("MapMarker", "실행 중인 스레드 없음")
            return
        }
        totallog(ApplcationLog,"스레드 종료 요청",true,false,false,false)
        //Log.d("MapMarker", "스레드 종료 요청")

        mapMarkerRunning = false
        mapMarkerThread?.interrupt()
        mapMarkerThread = null
    }

    // RF 에서 드론 좌표값 변경시 마다 드론 이미지 및 AAT 이미지 갱신
    fun movemarker(dronelatitude: Double, dronelongitude: Double, dronealt : Double, aatlatitude: Double, aatlongitude: Double, aatalt : Double){
        // 드론 좌표값 선언
        val dronelatLng = LatLng(dronelatitude, dronelongitude)
        // AAT 좌표값 선언
        val aatlatLng = LatLng(aatlatitude, aatlongitude)

        totallog("Autofly","receive set Marker AATlat : " + aatlatitude + " AATlong : " + aatlongitude +  " AATAlt : " + aatalt + " Dronelat : " + dronelatitude + " Dronelong : " + dronelongitude+ " Dronealt : " + dronealt,true,false,false,false)
        //Log.d(ContentValues.TAG, "receive set Marker AATlat : " + aatlatitude + " AATlong : " + aatlongitude +  " AATAlt : " + aatalt + " Dronelat : " + dronelatitude + " Dronelong : " + dronelongitude+ " Dronealt : " + dronealt)

        val dronestatus = "위도 : " + String.format("%.7f", dronelatitude) + "\n경도 : " +  String.format("%.7f", dronelongitude) + "\n고도 : " + String.format("%.1f", dronealt)
        val aatstatus = "위도 : " + aatlatitude + "\n경도 : " +  aatlongitude + "\n고도 : " + aatalt

        // 드론 좌표와 AAT 좌표를 기반으로 안테나 트레커 각도를 계산
        val epsilon = 1e-9
        var anternnadeg : Int

        if (AATYaw >= -epsilon && AATYaw <= 360.0 + epsilon) {
            anternnadeg = AATYaw.toInt()
        } else {
            anternnadeg = getBearing(aatlatitude,aatlongitude,dronelatitude,dronelongitude)
        }

        if (markerDrone == null) {
            // 처음 마커 추가
            markerDrone = googleMap?.addMarker(
                MarkerOptions()
                    .icon(getBitmapDescriptorFactoryDrone(R.drawable.img_drone_org,custommapzoom_scale)?.let {
                        BitmapDescriptorFactory.fromBitmap(
                            it
                        )
                    })
                    .position(dronelatLng)
                    .draggable(true)
                    .anchor(0.5F,0.5F)
                    .title("Drone")
                    .snippet(dronestatus)
            )
            markerDrone?.tag = "markerDrone"
        } else {
            // 기존 마커 위치, 타이틀, 스니펫만 갱신
            markerDrone!!.setIcon(getBitmapDescriptorFactoryDrone(
                R.drawable.img_drone_org,
                custommapzoom_scale
            )?.let {
                BitmapDescriptorFactory.fromBitmap(
                    it
                )
            })
            markerDrone!!.position = dronelatLng
            markerDrone!!.snippet = dronestatus
        }

        if (markeranternna == null) {
            // 처음 마커 추가
            markeranternna = googleMap?.addMarker(
                MarkerOptions()
                    .icon(
                        getBitmapDescriptorFactoryAnternna(R.drawable.img_aat_org_mk,
                            abs(alignAngle(anternnadeg.toDouble())) ,custommapzoom_scale // 안테나 각도계산 기반으로 이미지 회전
                        )?.let { BitmapDescriptorFactory.fromBitmap(it) })
                    .position(aatlatLng)
                    .draggable(true)
                    .anchor(0.5F,0.5F)
                    .title("Antenna")
                    .snippet(aatstatus)
            )
            markeranternna?.tag = "markeranternna"
        } else {
            // 기존 마커 위치, 타이틀, 스니펫만 갱신
            markeranternna!!.setIcon(getBitmapDescriptorFactoryDrone(
                R.drawable.img_aat_org_mk,
                custommapzoom_scale
            )?.let {
                BitmapDescriptorFactory.fromBitmap(
                    it
                )
            })
            markeranternna!!.position = aatlatLng
            markeranternna!!.rotation = abs(alignAngle(anternnadeg.toDouble()))
            markeranternna!!.snippet = aatstatus
        }

        val projection = googleMap!!.projection

        markerDrone?.position?.let {
            val point = projection.toScreenLocation(it)
            popup1.x = point.x.toFloat() - popup1.width / 2
            popup1.y = point.y.toFloat() - popup1.height  - 45
            popup1.text = dronestatus
        }

        markeranternna?.position?.let {
            val point = projection.toScreenLocation(it)
            popup2.x = point.x.toFloat() - popup2.width / 2
            popup2.y = point.y.toFloat() - popup2.height - 45
            popup2.text = aatstatus
        }
        dronepolyline?.remove()
        aatpolyline?.remove()

        val draonepolylineOptions = PolylineOptions().apply {
            color(0xFFFFFF00.toInt()) // 선 색상 (파란색)
            width(8f) // 선 두께
            add(markerDrone!!.position)
            add(markeranternna!!.position)
        }

        val aatlinestart = LatLng(aatlatitude, aatlongitude)
        val length = getLengthFromZoom(custommapzoom, aatlatitude, 150f)

        // 각도를 라디안으로 변환
        val aatlinerad = Math.toRadians(AATYaw)

        // 간단한 거리 오프셋 계산 (작은 거리만 정확)
        val aatlineendLat = aatlatitude + length * Math.cos(aatlinerad)
        val aatlineendLng = aatlongitude + length * Math.sin(aatlinerad)

        val aatlineend = LatLng(aatlineendLat, aatlineendLng)

        val aatpolylineOptions = PolylineOptions().apply {
            color(0xFF0000FF.toInt()) // 선 색상 (파란색)
            width(8f) // 선 두께
            add(aatlinestart, aatlineend)
        }

        if(aat_center_is) {
            moveCenterMap(aatlatitude,aatlongitude)
        }
        if(drone_center_is){
            moveCenterMap(dronelatitude,dronelongitude)
        }

        dronepolyline = googleMap?.addPolyline(draonepolylineOptions) // 지도에 선 추가
        aatpolyline = googleMap?.addPolyline(aatpolylineOptions)
    }

    // sp를 px로 변환하는 함수
    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }

    // 두 지점 사이의 각도 계산
    fun getBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int{
        val lat1_rad = ConvertDecimalDegreesToRadians(lat1)
        val lat2_rad = ConvertDecimalDegreesToRadians(lat2)
        val lon_diff_rad = ConvertDecimalDegreesToRadians(lon2-lon1)
        val x = sin(lon_diff_rad) * cos(lat2_rad)
        val y = cos(lat1_rad) * sin(lat2_rad) - sin(lat1_rad) * cos(lat2_rad) * cos(lon_diff_rad)
        return ((ConvertRadiansToDecimalDegrees(atan2(y, x)) + 360) % 360).toInt()
    }
    // getBearing 사용 함수1
    fun ConvertDecimalDegreesToRadians(deg: Double) : Double{
        return (deg * PI / 180);
    }

    // getBearing 사용 함수2
    fun ConvertRadiansToDecimalDegrees(rad: Double) : Double{
        return (rad * 180 / PI);
    }

    // 포인트 개수만큼 원형 좌표 생성 함수
    fun getDistanceMeterBetweenTwoLatLngCoordinate(
        latlng1: LatLng,
        latlng2: LatLng
    ): Double {
        val r = 6371e3  // 지구 반지름 (미터 단위)
        val lat1Rad = latlng1.latitude * PI / 180 // deg to rad
        val lat2Rad = latlng2.latitude * PI / 180 // deg to rad
        val deltaLat = (latlng2.latitude - latlng1.latitude) * PI / 180 // deg to rad
        val deltaLon = (latlng2.longitude - latlng1.longitude) * PI / 180 // deg to rad

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        //Log.d("MarkerList", "getDistanceMeterBetweenTwoLatLngCoordinate : " + (r * c).toInt())

        return r * c
    }

    // 원형 좌표 포인트 번호 생성
    fun calculatepointnum(betweendis : Int, speed : Int ): Int {
        //Log.d("MarkerList", "calculatepointnum : " + ((betweendis.toInt() / speed) - 1))
        return ((betweendis.toInt() / speed) - 1)
    }

    // 원형 좌표 값 기반 마커 생성
    private fun generateCirclePoints(center: LatLng, radius: Double, numPoints: Int, clockwise: Boolean){

        for (i in 0 until numPoints) {
            val theta = if (clockwise) {
                2.0 * Math.PI * (i + testag) / numPoints // 시계 방향 (0 → 360°)
            } else {
                2.0 * Math.PI * (numPoints - (i - testag)) / numPoints  // 반시계 방향 (360° → 0)
            }
            //Log.d(ContentValues.TAG, "generateCirclePoints theta : $theta")
            //val theta = 2.0 * Math.PI * i / numPoints // 각도 계산
            val latOffset = (radius / 111111.0) * cos(theta) // 위도 오프셋
            val lngOffset = (radius / (111111.0 * cos(Math.toRadians(center.latitude)))) * sin(theta) // 경도 오프셋

            val lat = center.latitude + latOffset
            val lng = center.longitude + lngOffset

            addMarker(LatLng(lat,lng))
        }
        addMarker(markerList[0].position)
    }

    fun bearingBetweenPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)

        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        var θ = Math.toDegrees(atan2(y, x))

        // 0~360도로 변환
        if (θ < 0) θ += 360.0
        return θ.toInt()
    }

    // map AAT 이미지 정리
    fun alignAngle(inputAngle: Double, imageOffset: Double = 90.0): Float {
        var result = inputAngle - imageOffset
        if (result < 0) result += 360
        return result.toFloat()
    }


    // USB 리시버

    private val usbAttachDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device =
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        totallog(DroneLog,"Device attached: ${it.deviceName}",true,false,true,false)
                        //Log.d("USB", "Device attached: ${it.deviceName}")
                        usbState = UsbState.WAITING
                        requestUsbPermission(it)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device =
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    totallog(DroneLog,"Device detached: ${device?.deviceName}",true,false,true,false)
                    //Log.d("USB", "Device detached: ${device?.deviceName}")

                    usbState = UsbState.DISCONNECTED
                    stopUsbCommunication()
                }
            }
        }
    }
    private val usbPermissionReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val device =
                            intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

                        if (intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false
                            )
                        ) {
                            device?.let {
                                usbState = UsbState.CONNECTED
                                onDeviceSelected(it)
                            }
                        } else {
                            totallog(DroneLog,"USB Permission denied",true,false,true,false)
                            //Log.d("USB", "Permission denied")
                            usbState = UsbState.WAITING
                        }
                    }
                }
            }
        }
    private fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        usbManager?.requestPermission(device, permissionIntent)
    }

    private fun stopUsbCommunication() {

        RF_connect_Set = false

        // 1) MAVLink 루프 종료 신호
        mavlinkDataProcessor?.stop()
        mavlinkDataProcessor = null

        totallog(DroneLog,"드론(텔레메트리) 연결 끊김",true,true,false,true)

        // 2) 🔥 이게 핵심: next()를 깨운다
        try { pipedIn?.close() } catch (_: Exception) {}
        try { pipedOut?.close() } catch (_: Exception) {}
        pipedIn = null
        pipedOut = null

        // 3) USB Serial 종료
        try { serialDevice?.close() } catch (_: Exception) {}
        serialDevice = null

        try { usbConnection?.close() } catch (_: Exception) {}
        usbConnection = null

        change_btn_con_drone_icon(0)
        //binding.btnDroneConStatus.background = null
        drone_center_is = false
    }


    // Bluetooth 리시버
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    //handleBluetoothDevice(intent)
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter == null) { // 디바이스가 블루투스를 지원하지 않을 때
                        totallog(BluetoothLog,"블루투스 미지원 기기입니다.",true,false,true,false)
                        //Toast.makeText(applicationContext, "블루투스 미지원 기기입니다.", Toast.LENGTH_LONG).show()
                    } else { // 디바이스가 블루투스를 지원 할 때

                        if (bluetoothAdapter!!.isEnabled) {
                            // 블루투스가 활성화 상태 (기기에 블루투스가 켜져있음)
                        } else { // 블루투스가 비 활성화 상태 (기기에 블루투스가 꺼져있음)
                            // 블루투스를 활성화 하기 위한 다이얼로그 출력
                            // 선택한 값이 onActivityResult 함수에서 콜백
                            //startActivityForResult(intent, 1)
                            totallog(BluetoothLog,"블루투스를 활성화 해주세요.",true,false,true,false)
                            //Toast.makeText(applicationContext, "블루투스를 활성화 해주세요.", Toast.LENGTH_LONG).show()
                        }

                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    controllerBT.terminateConnection()
                }
            }
        }
    }

    private fun onDeviceSelected(device: UsbDevice) {

        //stopUsbCommunication()

        usbConnection = usbManager?.openDevice(device)
        if (usbConnection == null) {
            totallog(DroneLog,"USB 디바이스 열기 실패",true,true,true,false)
            return
        }

        serialDevice = UsbSerialDevice.createUsbSerialDevice(device, usbConnection)
        if (serialDevice == null) {
            totallog(DroneLog,"serial 디바이스 생성 실패",true,true,true,false)
            return
        }

        if (!serialDevice!!.open()) {
            totallog(DroneLog,"serial port 열기 실패",true,true,true,false)
            return
        }

        serialDevice!!.setBaudRate(USBBaudrate)
        serialDevice!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
        serialDevice!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
        serialDevice!!.setParity(UsbSerialInterface.PARITY_NONE)
        serialDevice!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

        RF_connect_Set = true

        startMapMarkerThread()
        mapMarkerEnabled = true

        try {
            pipedOut = PipedOutputStream()
            pipedIn = PipedInputStream(pipedOut)

            // 🔁 USB → Pipe
            serialDevice!!.read { data ->
                try {
                    if (data != null && data.isNotEmpty()) {
                        pipedOut?.write(data)
                    }
                } catch (e: IOException) {
                    totallog(DroneLog,"Pipe write error",true,true,true,false)
                }
            }
            totallog(DroneLog,"드론(텔레메트리) 연결",true,true,false,true)

            totallog(DroneLog,"Drone MAVLink Data Start!",true,true,false,false)

            val mavlinkConnection = MavlinkConnection.create(
                pipedIn,
                UsbSerialOutputStream(serialDevice!!)
            )

            mavlinkDataProcessor = MavlinkDataProcessor(mavlinkConnection)

            // MAVLink 요청
            mavlinkDataProcessor!!.requestRawImuData(24)
            mavlinkDataProcessor!!.requestRawImuData(27)

            mavlinkDataProcessor!!.startMavlinkMessageListener { message ->
                message?.let {
                    processMAVLinkData(it)
                }
            }

            change_btn_con_drone_icon(2)

        } catch (e: IOException) {
            totallog(DroneLog,"USB init failed",true,true,true,false)
            stopUsbCommunication()
        }
    }

    // Mavlink REQ 데이터 파싱 및 확인
    @SuppressLint("SetTextI18n")
    private fun processMAVLinkData(message: MavlinkMessage<*>) {
        if (message.payload is Heartbeat) { // Mavlink Heartbeat 값 확인
            val heartbeatMessage = message.payload as Heartbeat
            runOnUiThread {
                val heartbeatText = ("""
                     System Type: ${heartbeatMessage.type().entry()}
                     Autopilot Type: ${heartbeatMessage.autopilot().entry()}
                     Base Mode: ${heartbeatMessage.baseMode().entry()}
                     Custom Mode: ${heartbeatMessage.customMode()}
                     System Status: ${heartbeatMessage.systemStatus().entry()}
                     """.trimIndent())
                //updateLogView("processMAVLinkData Received: $heartbeatText\n")
            }
        } else if (message.payload is RawImu) { // Mavlink RawImu(지자계센서값) 확인
            val imuMessage = message.payload as RawImu
            runOnUiThread {
                //Log.d("TAG", "imuMessage / magX : " + imuMessage.xmag().toString() + " magY : " + imuMessage.ymag().toString() + " magZ : " + imuMessage.zmag().toString())

                //var imutext = "x : " + imuMessage.xmag().toString()
                //updateLogView("imuMessage / magX : " + imuMessage.xmag().toString() + " magY : " + imuMessage.ymag().toString() + " magZ : " + imuMessage.zmag().toString() + "\n")
                //binding.RFGpslatTV.setText("Mag X: " + imuMessage.xmag().toString());
                //binding.RFGpslongTV.setText("Mag Y: " + imuMessage.ymag().toString());
                //binding.RFGpsaltTV.setText("Mag Z: " + imuMessage.zmag().toString());
            }
        } else if (message.payload is GpsRawInt) {  // Mavlink GPSIMU(GPS센서값) 확인
            val gpsMessage = message.payload as GpsRawInt
            runOnUiThread {
                dronelat = gpsMessage.lat() / 1E7 // Lat 값
                dronelong = gpsMessage.lon() / 1E7 // Long 값
                dronealt = gpsMessage.alt() / 1E3// Alt 값
                var isggooddronedata = isValidGpsCoordinate(dronelat.toString(),dronelong.toString(),dronealt.toString())
                if(isggooddronedata) {
                    ++drone_data_true_count
                }
                else {
                    ++drone_data_fail_count
                }
                updatedroneLogview(dronelat.toString(),dronelong.toString(),dronealt.toString())
                if (bluetoothSocket?.isConnected == true){
                    var error_rate = 0
                    if(drone_data_fail_count != 0)
                        error_rate = drone_data_fail_count/(3+drone_data_fail_count)
                    var dronedatafm = String.format("Lat : %f , Long : %f , Alt : %.2f / Total : %d , Truecount : %d, Failcount : %d, Error rate :  : %d ",
                                        dronelat,dronelong,dronealt,drone_data_true_count+drone_data_fail_count,drone_data_true_count,drone_data_fail_count,error_rate)
                    //if(Logchange) newupdateLogView("V&V_Dronedata",dronedatafm)
                }
            }
        }
    }
    // USB 디바이스 선택 및 실행

    //Bluetooth 권한 확인
    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()

        if (VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }

    // Bluetooth fun

    fun selectBluetoothDevice() {
        // 이미 페어링 되어있는 블루투스 기기를 찾음

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        devices = bluetoothAdapter?.getBondedDevices()

        // 페어링 된 디바이스의 크기를 저장
        pariedDeviceCount = devices!!.size

        // 페어링 되어있는 장치가 없는 경우
        if (pariedDeviceCount == 0) {
            // 페어링을 하기위한 함수 호출
            totallog(AATLog,"먼저 Bluetooth 설정에 들어가 페어링 해주세요",true,false,true,false)
            //Toast.makeText(applicationContext, "먼저 Bluetooth 설정에 들어가 페어링 해주세요", Toast.LENGTH_SHORT).show()
        } else {
            // 디바이스를 선택하기 위한 다이얼로그 생성
            val builder = AlertDialog.Builder(this)
            builder.setTitle("페어링 되어있는 블루투스 디바이스 목록")

            val deviceList = devices!!.map { it.name }.toTypedArray()

            builder.setItems(deviceList) { dialog, which ->
                connectBluetooth(deviceList[which])
            }

            builder.setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }

            val alertDialog = builder.create()
            alertDialog.show()
        }
    }

    private fun connectBluetooth(deviceName: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // 이름으로 디바이스 찾기
        val targetDevice = devices?.firstOrNull { it.name == deviceName }
        if (targetDevice == null) {
            totallog(BluetoothLog,"선택한 Bluetooth 디바이스를 찾을 수 없습니다.",true,false,true,false)
            //Toast.makeText(this, "선택한 디바이스를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 이전 연결/수신 정리
        initdisconnectBluetooth()
        mapMarkerEnabled = true

        Thread {
            try {
                val socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter?.cancelDiscovery()

                socket.connect() // 여기서 블록

                // 연결 성공
                bluetoothSocket = socket
                inputStream = socket.inputStream
                outputStream = bluetoothSocket!!.outputStream
                BT_connect_Set = true

                runOnUiThread {
                    totallog(BluetoothLog,"$deviceName 연결",true,true,true,true)
                    change_btn_con_aat_icon(2) // 연결/통신중 아이콘
                    binding.btnAatConStatus.background = null
                    aat_center_is = false
                }

                // ✅ 연결된 상태에서만 수신 스레드 시작
                startListeningForMessages()
                startMapMarkerThread()

            } catch (e: Exception) {
                runOnUiThread {
                    totallog(BluetoothLog,"블루투스 연결 실패 : "+ e.message,true,true,true,true)
                    change_btn_con_aat_icon(0) // 끊김/미연결 아이콘
                    change_btn_con_manual_icon(0)
                }

                //disconnectBluetooth()
            }
        }.start()
    }

    fun disconnectBluetooth() {
        totallog(BluetoothLog,"블루투스 연결 끊김",true,true,false,true)
        stopListeningForMessages()

        try {
            inputStream?.close()
        } catch (_: Exception) { }
        inputStream = null

        try {
            bluetoothSocket?.close()
        } catch (_: Exception) { }
        bluetoothSocket = null

        BT_connect_Set = false

        runOnUiThread {
            change_btn_con_aat_icon(0) // 미연결/끊김 아이콘
            change_btn_con_manual_icon(0)
        }
    }

    fun initdisconnectBluetooth() {
        totallog(BluetoothLog,"initdisconnect Bluetooth",true,true,false,false)
        stopListeningForMessages()

        try {
            inputStream?.close()
        } catch (_: Exception) { }
        inputStream = null

        try {
            bluetoothSocket?.close()
        } catch (_: Exception) { }
        bluetoothSocket = null

        BT_connect_Set = false

        runOnUiThread {
            change_btn_con_aat_icon(0) // 미연결/끊김 아이콘
            change_btn_con_manual_icon(0)
        }
    }

    // AAT StartREQ 전달 문
    @Throws(IOException::class)
    private fun sendStartREQ() {
        // StartREQ 생성
        val startREQ = ByteArray(12)
        startREQ[0] = 0xAA.toByte() // STX
        startREQ[1] = 0x08.toByte() // LEN 08 > 10
        memcpy(startREQ, 2, AAT_ID, 0, 4) // App_ID
        memcpy(startREQ, 6, App_ID, 0, 4) // AAT_ID
        startREQ[10] = 0xFF.toByte() // Checksum / 0F > FF
        startREQ[11] = 0x55.toByte() // ETX

        // AAT 전달용 StartREQ protocol 전달
        try {
            outputStream!!.write(startREQ)
            totallog(AATLog,"sendStartREQ : "+bytesToHex(startREQ),false,true,false,false)
            //if(!Logchange) newupdateLogView("sendStartREQ",bytesToHex(startREQ))
        } catch (e: IOException) {
            totallog(AATLog,"Error sending sendStartREQ : "+bytesToHex(startREQ),false,true,false,false)
            throw e
        }
    }

    // AAT StopREQ 전달 문
    @Throws(IOException::class)
    private fun sendStopREQ() {
        // AAT 전달용 StopREQ 생성
        val stopREQ = ByteArray(9)
        stopREQ[0] = 0xAA.toByte() // STX
        stopREQ[1] = 0x05.toByte() // LEN
        memcpy(stopREQ, 2, AAT_ID, 0, 4) // App_ID
        stopREQ[6] = 0x0A.toByte() // Request
        stopREQ[7] = 0xFF.toByte() // Checksum
        stopREQ[8] = 0x55.toByte() // ETX

        // StopREQ protocol 전달
        try {
            outputStream!!.write(stopREQ)
            totallog(DroneLog,"sendStopREQ : "+bytesToHex(stopREQ),true,true,true,false)
        } catch (e: IOException) {
            totallog(DroneLog,"Error sending StopREQ",true,false,true,false)
            throw e
        }
    }

    // AAT StopREQ 전달 문
    @Throws(IOException::class)
    private fun sendCMDREQ(SendCMDREQ_MSG : String , Command : ByteArray) {
        // AAT 전달용 sendCMDREQ 생성
        val CMDREQ = ByteArray(10)
        CMDREQ[0] = 0xAA.toByte() // STX
        CMDREQ[1] = 0x06.toByte() // LEN
        memcpy(CMDREQ, 2, AAT_ID, 0, 4) // App_ID
        memcpy(CMDREQ, 6, Command, 0, 2) // AAT_ID
        CMDREQ[8] = 0xFF.toByte() // Checksum
        CMDREQ[9] = 0x55.toByte() // ETX

        // sendCMDREQ protocol 전달
        try {
            outputStream!!.write(CMDREQ)
            totallog(DroneLog,"sendCMDREQ : "+bytesToHex(CMDREQ),true,true,true,false)
            CMD_REQ_SW = true
        } catch (e: IOException) {
            totallog(DroneLog,"Error sending sendCMDREQ",true,false,true,false)
            throw e
        }
    }

    @Throws(IOException::class)
    private fun Send_AAT_CMD_REQ_Manual_Angle(angle : Int) {
        // AAT 전달용 StopREQ 생성
        val AAT_CMD_REQ = ByteArray(10)
        AAT_CMD_REQ[0] = 0xAA.toByte() // STX
        AAT_CMD_REQ[1] = 0x06.toByte() // LEN
        memcpy(AAT_CMD_REQ, 2, AAT_ID, 0, 4) // App_ID
        if(isManualAngle){
            require(angle in 0..359) { "angle must be 0..359, but was $angle" }

            val value = 0xB000 or (angle and 0x0FFF) // 상위 nibble B, 하위 12비트에 angle
            AAT_CMD_REQ[6] = ((value ushr 8) and 0xFF).toByte()
            AAT_CMD_REQ[7] = (value and 0xFF).toByte()
        }
        else {
            AAT_CMD_REQ[6] = 0x00.toByte() // command
            AAT_CMD_REQ[7] = 0x00.toByte() // command
        }
        AAT_CMD_REQ[8] = 0xFF.toByte() // Checksum
        AAT_CMD_REQ[9] = 0x55.toByte() // ETX

        // StopREQ protocol 전달
        try {
            outputStream!!.write(AAT_CMD_REQ)
            //updateLogView("sendStopREQ",bytesToHex(stopREQ))
            totallog(DroneLog,"Send_AAT_CMD_REQ_Manual_Angle : "+bytesToHex(AAT_CMD_REQ),true,true,true,false)
            manualanglemarker(AATlat,AATlong,0)
            isManualAngle_touch = false
        } catch (e: IOException) {
            totallog(DroneLog,"Error sending Send_AAT_CMD_REQ",true,false,true,false)
            throw e
        }

    }

    @Throws(IOException::class)
    private fun Send_AAT_CMD_REQ_Manual_Angle_tilt(angle : Int) {
        // AAT 전달용 StopREQ 생성
        val AAT_CMD_REQ = ByteArray(10)
        AAT_CMD_REQ[0] = 0xAA.toByte() // STX
        AAT_CMD_REQ[1] = 0x06.toByte() // LEN
        memcpy(AAT_CMD_REQ, 2, AAT_ID, 0, 4) // App_ID
        if(isManualAngle){
            require(angle in 0..90) { "angle must be 0..90, but was $angle" }

            val value = 0xB200 or (angle and 0x0FFF) // 상위 nibble B, 하위 12비트에 angle
            AAT_CMD_REQ[6] = ((value ushr 8) and 0xFF).toByte()
            AAT_CMD_REQ[7] = (value and 0xFF).toByte()
        }
        else {
            AAT_CMD_REQ[6] = 0x00.toByte() // command
            AAT_CMD_REQ[7] = 0x00.toByte() // command
        }
        AAT_CMD_REQ[8] = 0xFF.toByte() // Checksum
        AAT_CMD_REQ[9] = 0x55.toByte() // ETX

        // StopREQ protocol 전달
        try {
            outputStream!!.write(AAT_CMD_REQ)
            totallog(DroneLog,"Send_AAT_CMD_REQ_Manual_Angle_tilt : "+bytesToHex(AAT_CMD_REQ),true,true,true,false)
            manualanglemarker(AATlat,AATlong,0)
            isManualAngle_touch = false
        } catch (e: IOException) {
            totallog(DroneLog,"Error sending Send_AAT_CMD_REQ_Manual_Angle_tilt",true,false,true,false)
            throw e
        }

    }

    // RF 위도 경도 고도 AAT 전달
    private fun Send_Drone_LOC_IND(drone_lat: Double, drone_long: Double, drone_alt: Double) {

        val droneLatitudeIntPart: Long = drone_lat.toLong() //드론 Lat 값 선언 및 형 변환
        val droneLatitudeFracPart: Long =
            ((drone_lat - droneLatitudeIntPart) * 10000000.0).toLong() //(long)((droneLatitude - droneLatitudeIntPart) * 10000000.0);
        val droneLongitudeIntPart: Long = drone_long.toLong() // 드론 Long 선언 및 형 변환
        val droneLongitudeFracPart: Long =
            ((drone_long - droneLongitudeIntPart) * 10000000.0).toLong() //(long)((droneLongitude - droneLongitudeIntPart) * 10000000.0);


        // AAT 전달용 DroneLOCIND 생성
        val gpsData = ByteArray(31)
        gpsData[0] = 0xAA.toByte() // STX
        gpsData[1] = 0x1B.toByte() // LEN
        memcpy(gpsData, 2, App_ID, 0, 4) // App_ID
        memcpy(gpsData, 6, longToBytes(droneLatitudeIntPart), 0, 4) //drone_Latitude integer part
        memcpy(
            gpsData,
            10,
            longToBytes(droneLatitudeFracPart),
            0,
            4
        ) // drone_Latitude fractional part
        memcpy(
            gpsData,
            14,
            longToBytes(droneLongitudeIntPart),
            0,
            4
        ) // drone_Longitude integer part
        memcpy(
            gpsData,
            18,
            longToBytes(droneLongitudeFracPart),
            0,
            4
        ) // drone_Longitude fractional part
        memcpy(
            gpsData,
            22,
            floatToBytes(drone_alt.toFloat() /*droneAltitude*/),
            0,
            4
        ) // drone_Altitude
        gpsData[26] = 0x00.toByte() // Angle
        gpsData[27] = 0x00.toByte() // Angle
        gpsData[28] = 0x0F.toByte() // Drone status
        gpsData[29] = 0xFF.toByte() // Checksum
        gpsData[30] = 0x55.toByte() // ETX

        // AAT 전달용 DroneLOCIND protocol 전달
        try {
            outputStream!!.write(gpsData)
            //if(!Logchange) newupdateLogView("Send_Drone_LOC_IND",bytesToHex(gpsData))
            ++CountSendDroneLOC_IND
            aat_IND_time = System.nanoTime()
            //var aatDuration = String.format("IND_REQ_time : %.2f ms, IND_LOC_time : %.2f ms, Processing time : %.2f ms",
            //    aat_REQ_time/1_000_000.0, aat_IND_time/1_000_000.0, (aat_IND_time-aat_REQ_time)/1_000_000.0)
            var seletedata = "Send_Drone_LOC_IND / Count : " + CountSendDroneLOC_IND.toString() + " / GPSupdatecount : " + CountSendDroneLOC_gps.toString() + " / LatInt = " + droneLatitudeIntPart + " / LatFrac = " + droneLatitudeFracPart + " / LonInt = " + droneLongitudeIntPart + " / LonFrac = " + droneLongitudeFracPart
            totallog(DroneLog,"Send_Drone_LOC_IND : "+seletedata,true,true,true,false)
        } catch (e: IOException) {
            e.printStackTrace()
            totallog(DroneLog,"Error sending Send_Drone_LOC_IND",true,false,true,false)
        }
    }

    fun startListeningForMessages() {
        totallog(AATLog,"Bluetooth(AAT) message Thread start!",true,true,true,false)

        // 이미 리스닝 중이면 중복 방지
        if (isListening) {
            totallog(AATLog,"Already AAT message Thread, skip start",true,false,true,false)
            return
        }

        val stream = inputStream
        if (stream == null) {
            totallog(AATLog,"inputStream is null, cannot start AAT message Thread",true,false,true,false)
            return
        }

        isListening = true

        listenThread = Thread {
            try {
                val START_BYTE: Byte = 0xAA.toByte()
                val END_BYTE: Byte = 0x55.toByte()

                var state = 0 // 0: 대기, 1: 길이 수신, 2: 데이터 수신
                var expectedLength = 0
                val packetBuffer = mutableListOf<Byte>()

                while (isListening) {
                    val byte = try {
                        stream.read()
                    } catch (e: Exception) {
                        totallog(AATLog,"AAT Stream read error",true,false,true,false)
                        break
                    }

                    if (!isListening) break

                    if (byte == -1) {
                        totallog(AATLog,"AAT Stream closed by remote device",true,false,true,false)
                        break
                    }

                    val receivedByte = byte.toByte()

                    when (state) {
                        0 -> {
                            if (receivedByte == START_BYTE) {
                                packetBuffer.clear()
                                packetBuffer.add(receivedByte)
                                state = 1
                            }
                        }

                        1 -> {
                            expectedLength = receivedByte.toUByte().toInt()
                            packetBuffer.add(receivedByte)
                            state = 2
                        }

                        2 -> {
                            packetBuffer.add(receivedByte)
                            val totalLength = 1 + 1 + expectedLength + 1 + 1 // START + LEN + DATA + CHECKSUM + END

                            if (packetBuffer.size == totalLength) {
                                val checksumIndex = packetBuffer.size - 2
                                val endByte = packetBuffer.last()
                                val receivedChecksum = packetBuffer[checksumIndex]

                                // TODO: 실제 체크섬 계산 로직으로 교체
                                val calculatedChecksum = 0x00.toByte()

                                if (endByte == END_BYTE && receivedChecksum == calculatedChecksum) {
                                    val packet = packetBuffer.toByteArray()
                                    if (isListening) {
                                        handler.post {
                                            try {
                                                receiveREQ(packet)
                                            } catch (e: NullPointerException) {
                                                totallog(AATLog,"NullPointerException in receiveREQ",true,false,true,false)
                                            } catch (e: Exception) {
                                                //(AATLog,"Exception in receiveREQ",true,false,true,false)
                                            }
                                        }
                                    }
                                } else {
                                    totallog(AATLog,"AAT Invalid packet - checksum or end byte mismatch",true,false,true,false)
                                }

                                state = 0
                                packetBuffer.clear()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                totallog(AATLog,"Bluetooth read error",true,false,true,false)
            } finally {
                isListening = false
                BT_connect_Set = false
                runOnUiThread {
                    totallog(AATLog,"Bluetooth(AAT) message Thread stop!",true,true,true,false)
                    change_btn_con_aat_icon(0) // 끊김 상태 아이콘
                    disconnectBluetooth()
                }
            }
        }.apply {
            start()
        }
    }

    fun stopListeningForMessages() {
        totallog(AATLog,"Bluetooth(AAT) message Thread stop!",true,false,true,false)
        isListening = false

        try {
            listenThread?.interrupt()
        } catch (_: Exception) { }

        listenThread = null
    }

    // AAT REQ 데이터 확인용 함수
    fun receiveREQ(buffer: ByteArray) {
        //Log.d(ContentValues.TAG, "packet " + bytesToHex(buffer))
        val endIndex = buffer.indexOf(0x55.toByte())
        //val slicebuffer = buffer.copyOfRange(0,50)
        val slicebuffer = if (endIndex != -1 && buffer[endIndex-1] == 0x00.toByte()) buffer.copyOfRange(0,endIndex+1) else buffer
        if(buffer[0] == 0xAA.toByte() && buffer[1] == 0x08.toByte() && buffer[10] == 0x00.toByte()) { //AAT_Ready_Brdcst
            AAT_ID = byteArrayOf(buffer[2],buffer[3],buffer[4],buffer[5])
            sendStartREQ()
            totallog(DroneLog,"AAT_Ready_Brdcst : "+bytesToHex(slicebuffer),true,true,true,false)
        } else if (buffer[0] == 0xAA.toByte() && buffer[1] == 0x1D.toByte() && buffer[31] == 0x00.toByte() && buffer[32] == 0x55.toByte()) { // Drone_LOC_REQ
            aat_REQ_time = System.nanoTime()
            // Parse the AAT_GPS coordinates
            // AAT 데이터 파싱
            AAT_ID = byteArrayOf(buffer[2],buffer[3],buffer[4],buffer[5])
            val aatLatIntPart = bytesToLong(buffer, 6)
            val aatLatFracPart = bytesToLong(buffer, 10)
            val aatLonIntPart = bytesToLong(buffer, 14)
            val aatLonFracPart = bytesToLong(buffer, 18)
            AATlat = aatLatIntPart + (aatLatFracPart / 1e7) // AAT lat 값 파싱 및 변환
            AATlong = aatLonIntPart + (aatLonFracPart / 1e7) // AAT long 값 파싱 및 변환
            val altbyteArray = byteArrayOf(buffer[22], buffer[23], buffer[24], buffer[25]) // 1.0f에 해당하는 바이트 (리틀 엔디안)
            AATalt = bytesToFloat(altbyteArray).toDouble()
            val yaw_byteArray = byteArrayOf(buffer[26], buffer[27])
            val intyawValue = bytesToShort(yaw_byteArray)
            //val tilt_byteArray = byteArrayOf(buffer[28], buffer[29])
            //val inttiltValue = bytesToShort(tilt_byteArray)
            AATYaw = intyawValue.toDouble()
            val tilt = byteToInt(buffer[28])
            AATrssi = byteToInt(buffer[29])
            val tiltandRSSI = String.format(tilt.toString() + " / " + AATrssi.toString())
            //AATlat = bytesToFloat(buffer, 22)

            val status = buffer[30]
            //val statustest = bytesToHex(buffer,26,2)
            //Log.d(dronelogv, "Receive test Drone_Loc_Req data : " + statustest)
            var statusStr = ""
            when (status) {
                0x00.toByte() -> statusStr = "AAT OK"
                //0xAx : AAT not ready

                0xA0.toByte() -> statusStr = "AAT is under resetting"
                0xA1.toByte() -> statusStr = "Compass Calibration goes on"
                0xA2.toByte() -> statusStr = "GPS is under searching SATs"
                0xA3.toByte() -> statusStr = "YAW & TILT is initializing"
                //0xBx : Battery status
                //0xB.toByte() -> statusStr = "GPS inactive"
                //0xCx : AAT action failed
                0xC0.toByte() -> statusStr = "unknown AAT problem"
                0xC1.toByte() -> statusStr = "Compass calibration failed"
                0xC2.toByte() -> statusStr = "GPS failed, default position set"
                0xC3.toByte() -> statusStr = "TILT sensor run over 90°"
                //0xDx : Target problem
                0xD0.toByte() -> statusStr = "target available"
                0xD1.toByte() -> statusStr = "target GPS not available"
                0xD2.toByte() -> statusStr = "target antenna tracking unavailable"
            }
            if(isValidAATGpsCoordinate(AATlat.toString(),AATlong.toString(),AATalt.toString(),AATYaw.toString())){
                ++aat_data_true_count
            }
            else {
                ++aat_data_fail_count
            }

            var aat_error_rate = 0
            if(aat_data_fail_count != 0)
                aat_error_rate = aat_data_fail_count/(aat_data_true_count+aat_data_fail_count)

            // AAT 상태값 파싱 및 출력
            if(!isManualAngle) {
                Send_Drone_LOC_IND(dronelat, dronelong, dronealt)
            }
            updateaatLogview(AATlat.toString(),AATlong.toString(),AATalt.toString(),intyawValue.toString(),tiltandRSSI,statusStr)
            totallog(DroneLog,"Drone_LOC_REQ / " + "status > "+ statusStr +" : " +bytesToHex(slicebuffer),true,true,true,false)

        } else if(buffer[0] == 0xAA.toByte() && buffer[1] == 0x0B.toByte() && buffer[13] == 0x00.toByte() && buffer[14] == 0x55.toByte()){ // AAT_CMD_IND
            val value = bytesToHex(buffer,10,2)
            CMD_REQ_SW = false
            if(isManualAngle) {
                AATrssi = byteToInt(buffer[12])
                val tiltandRSSI = String.format("- / " + AATrssi.toString())
                updateaatLogview(AATlat.toString(),AATlong.toString(),AATalt.toString(),"-",tiltandRSSI,"ManualAngle")
                totallog(DroneLog,"AAT_CMD_IND : "+bytesToHex(slicebuffer),true,true,true,false)
                isManualAngle_touch = true
                manualanglemarker(AATlat,AATlong,1)
                AATYaw = ManualAngle.toDouble()
            }

        }
        else if (buffer[0] == 0xAA.toByte() && buffer[1] == 0x05.toByte() && buffer[6] == 0x0B.toByte()) { // STOP_ACK
            // Handle stop acknowledgment
            stopCommunication()
            totallog(DroneLog,"STOP_ACK : "+bytesToHex(slicebuffer),true,true,true,false)
        }
    }

    // AAT 데이터 형변환을 위한 함수 정의

    // 메모리 함수
    private fun memcpy(dest: ByteArray, destPos: Int, src: ByteArray, srcPos: Int, length: Int) {
        for (i in 0 until length) {
            dest[destPos + i] = src[srcPos + i]
        }
    }

    // long > Bytes 변환
    private fun longToBytes(value: Long): ByteArray {
        val bytes = ByteArray(4) // 고정 소수점 부분은 4바이트로 처리
        for (i in 0..3) {
            bytes[i] = ((value shr (8 * i)) and 0xFFL).toByte()
        }
        return bytes
    }

    // float > Bytes 변환
    private fun floatToBytes(value: Float): ByteArray {
        val intValue = java.lang.Float.floatToIntBits(value)
        val bytes = ByteArray(4)
        for (i in 0..3) {
            bytes[i] = ((intValue shr (8 * i)) and 0xFF).toByte()
        }
        return bytes
    }

    // Bytes > Hex 변환 > App 에서 AAT 전달 용으로 사용
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString()
    }

    // Bytes > Hex 변환 (자리수 입력) > AAT에서 App으로 온 데이터 파싱용으로 사용
    private fun bytesToHex(bytes: ByteArray, start: Int, length: Int): String {
        val sb = StringBuilder()
        for (i in start until start + length) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        return sb.toString()
    }

    // long > Bytes 변환 > AAT에서 App으로 온 데이터 파싱용으로 사용
    private fun bytesToLong(bytes: ByteArray, start: Int): Long {
        var value: Long = 0
        for (i in 0..3) {
            value = value or ((bytes[start + i].toLong() and 0xffL) shl (8 * i))
        }

        val hexString = bytesToHex(bytes, start, 4)
        //Log.d(ContentValues.TAG, "Bytes to Long (HEX): $hexString")

        return value
    }

    // 2byte > 숫자로 변환 > AAT에서 App으로 온 데이터 파싱용으로 사용
    private fun bytesToShort(bytes: ByteArray): Short {
        return ((bytes[1].toInt() shl 8) or (bytes[0].toInt() and 0xFF)).toShort()
    }

    // byte > 숫자로 변환
    fun byteToInt(byteValue: Byte): Int {
        return byteValue.toInt() and 0xFF
    }

    fun bytesToFloat(bytes: ByteArray): Float {
        require(bytes.size == 4) { "Byte array must be exactly 4 bytes" }
        return ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN) // 필요에 따라 BIG_ENDIAN으로 변경
            .float
    }

    // Messages 정지
    private fun stopCommunication() {
        try {
            if (bluetoothSocket != null) {
                sendStopREQ()
                BT_connect_Set = false
                bluetoothSocket!!.close()
            }
            handler.removeCallbacks(autoflystatusUpdateTask!!)
            change_btn_con_aat_icon(0)
            totallog(DroneLog,"bluetooth 연결해지",true,true,false,false)
        } catch (e: IOException) {
            e.printStackTrace()
            totallog(DroneLog,"Error Stopped bluetooth communication",true,true,false,false)
        }
    }


    // 로그 화면 드론 좌표값 업데이트
    private fun updatedroneLogview(lat : String, long : String, alt : String) {
        binding.tvMasboxDronelat.text = "lat : " + String.format("%.7f", lat.toDouble())
        binding.tvMasboxDronelong.text = "lon : " + String.format("%.7f",long.toDouble())
        binding.tvMasboxDronealt.text = "alt : " + String.format("%.1f",alt.toDouble())

    }

    // 로그 화면 AAT 좌표값 업데이트
    private fun updateaatLogview(lat : String, long : String, alt : String, yaw : String, tiltRSSI : String, status : String) {
        binding.tvMasboxAatllat.text = "lat : " + String.format("%.7f", lat.toDouble())
        binding.tvMasboxAatlong.text = "lon : " + String.format("%.7f",long.toDouble())
        binding.tvMasboxAatalt.text = "alt : " + String.format("%.1f",alt.toDouble())
        binding.tvMasboxAatyaw.text = "yaw : " + yaw
        binding.tvMasboxAattilt.text = "tilt/RSSI : " + tiltRSSI
        binding.tvMasboxAatstatus.text = "status : " + status
    }

    fun isValidGpsCoordinate(latStr: String?, lonStr: String?, altStr: String?): Boolean {
        val lat = latStr?.toDoubleOrNull()
        val lon = lonStr?.toDoubleOrNull()
        val alt = altStr?.toDoubleOrNull()

        if (lat == null || lon == null || alt == null) return false

        val isLatValid = lat in -90.0..90.0
        val isLonValid = lon in -180.0..180.0
        val isAltValid = alt in -500.0..10000.0

        return isLatValid && isLonValid && isAltValid
    }

    fun isValidAATGpsCoordinate(latStr: String?, lonStr: String?, altStr: String?, yawStr: String?): Boolean {
        val lat = latStr?.toDoubleOrNull()
        val lon = lonStr?.toDoubleOrNull()
        val alt = altStr?.toDoubleOrNull()
        val yaw = yawStr?.toDoubleOrNull()

        if (lat == null || lon == null || alt == null || yaw == null) return false

        val isLatValid = lat in -90.0..90.0
        val isLonValid = lon in -360.0..360.0
        val isAltValid = alt in -500.0..10000.0
        val isYawValid = yaw in -360.0..360.0

        return isLatValid && isLonValid && isAltValid && isYawValid
    }

    private fun showNumberInputDialog(
        title: String,
        message: String,
        onOk: (Int) -> Unit
    ) {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "숫자를 입력하세요"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val text = editText.text.toString()
                val value = text.toIntOrNull()

                if (value == null) {
                    Toast.makeText(this, "숫자를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                } else {
                    onOk(value)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private val uiHandler = Handler(Looper.getMainLooper())

    fun totallog(
        title: String,
        message: String,
        Logger_writeLog: Boolean,
        newupdateLogView: Boolean,
        sys_Log: Boolean,
        Toast: Boolean
    ) {
        // 로그는 어느 스레드든 OK
        if (Logger_writeLog) {
            Logger.writeLog(title, message)
        }
        if (sys_Log) {
            Log.d(title, message)
        }

        // UI 관련은 무조건 메인 스레드로
        if (newupdateLogView || Toast) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                handleUiLog(title, message, newupdateLogView, Toast)
            } else {
                uiHandler.post {
                    handleUiLog(title, message, newupdateLogView, Toast)
                }
            }
        }
    }

    private fun handleUiLog(
        title: String,
        message: String,
        newupdateLogView: Boolean,
        Toast: Boolean
    ) {
        if (newupdateLogView) {
            newupdateLogView(title, message)
        }
        if (Toast) {
            android.widget.Toast
                .makeText(this, message, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    // 내부 Message 기록
    private fun newupdateLogView(send: String, message: String) {
        // 메인 스레드가 아니면 메인으로 넘기고 종료
        if (Looper.myLooper() != Looper.getMainLooper()) {
            uiHandler.post { newupdateLogView(send, message) }
            return
        }

        val maxline = 200

        val currentText = binding.tvLog.text?.toString().orEmpty()
        val lines = if (currentText.isBlank()) mutableListOf<String>()
        else currentText.split("\n").toMutableList()

        val localDateTime = LocalDateTime.now()
        val updateString = "$localDateTime : [$send] - $message"

        // 최신 로그를 맨 위에
        lines.add(0, updateString)

        // maxline 유지
        if (lines.size > maxline) {
            lines.subList(maxline, lines.size).clear()
        }

        binding.tvLog.text = lines.joinToString("\n")
    }
}

