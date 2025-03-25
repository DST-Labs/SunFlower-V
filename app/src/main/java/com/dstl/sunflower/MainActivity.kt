package com.dstl.sunflower

import android.Manifest
import android.annotation.SuppressLint
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
import android.hardware.usb.UsbManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import mavlink.MavlinkDataProcessor
import mavlink.UsbHelper
import mavlink.UsbSerialOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.Thread.sleep
import java.time.LocalDateTime
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, OnMarkerDragListener, GoogleApiClient.OnConnectionFailedListener,
    OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding // MainActivity의 binding 선언

    //private val controllerUSB = ControllerUSB() // USB controller 선언
    private val controllerBT = ControllerBT() // bluetooth controller 선언
    private val controllerSerial = ControllerSerial(this) // Serial controller 선언

    // USB 선언
    private var usbDevice: UsbDevice? = null // USB 디바이스 목록
    private var usbManager : UsbManager? = null // USB 디바이스 메니저
    private var usbHelper: UsbHelper? = null
    //private var usbdevicename : String? = null // USB 디바이스 이름
    private val ACTION_USB_PERMISSION = "com.example.mavlinktest.USB_PERMISSION"
    private val USBBaudrate = 57600
    private var mavlinkDataProcessor : MavlinkDataProcessor? = null


    // Bluetooth 선언

    private var bluetoothAdapter: BluetoothAdapter? = null // 블루투스 어댑터
    private var bluetoothDevice: BluetoothDevice? = null // 블루투스 디바이스 목록
    private var devices: Set<BluetoothDevice>? = null // 블루투스 디바이스 데이터 셋
    private var bluetoothSocket: BluetoothSocket? = null // 블루투스 소켓
    var pariedDeviceCount: Int = 0 // 블루투스 페어링 디바이스 크기

    private var outputStream: OutputStream? = null // 블루투스에 데이터를 출력하기 위한 출력 스트림
    private var inputStream: InputStream? = null // 블루투스에 데이터를 입력하기 위한 입력 스트림

    private var handler = Handler(Looper.getMainLooper()) // 블루투스 메세지용 핸들러

    val statusUpdateTask: Runnable? = null

    // AAT 통신 변수 선언
    private val App_ID: ByteArray =
        byteArrayOf(0xAA.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte()) // AAT ID
    private val AAT_ID: ByteArray =
        byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte()) // APP ID

    var AAT_REQ_ID: ByteArray =
        byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte())

    // AAT_CMD_REQ 정의
    private val CMD_Command_SYNC: ByteArray =
        byteArrayOf(0x00.toByte(), 0x00.toByte()) // STNC

    private val CMD_Command_AAT_Reset: ByteArray =
        byteArrayOf(0xA2.toByte(), 0x00.toByte()) // AAT ID

    private val CMD_Command_AAT_Init_test: ByteArray =
        byteArrayOf(0xA1.toByte(), 0x00.toByte()) // AAT Inti test

    private val CMD_Command_Set_arm_upright: ByteArray =
        byteArrayOf(0xB1.toByte(), 0x00.toByte()) // AAT Inti test




    // map 변수 선언
    lateinit var providerClient: FusedLocationProviderClient
    lateinit var apiClient: GoogleApiClient
    var googleMap: GoogleMap? = null

    // GPS 관련 변수 선언
    var AATlat = 37.488006 // AAT lat
    var AATlong = 127.008915 // 테스트용 AAT long 더미
    var AATalt = 30.0
    //val Distanceval = 6371e3 // 안테나 트래커 방향 전환용 변수
    val PI = 3.14159265358979323846 // 안테나 트래커 방향 전환용 변수

    var dronelat = 0.0
    var dronelong = 0.0
    var dronealt = 30.0
    var markerDrone : Marker? = null // 드론 이미지
    var markeranternna : Marker? = null // AAT 이미지


    var testOnoff : Boolean = false

    private var autoflyhandler = Handler(Looper.getMainLooper())
    var autoflystatusUpdateTask: Runnable? = null
    var Autoflyis = false

    private val markerList = mutableListOf<Marker>()
    private val automarkerList = mutableListOf<LatLng>()
    private var polyline: Polyline? = null // 마커간 선 그리기

    private var Testdronespeed : Int = 100 // 비행 속도 설정 단위 :  m/s
    private var Testradius : Double = 1500.0 // 비행 원형 반지름
    private var TestradiusPoint :Int = 12 // 비행 포인트 개수 12
    var testcricleSW : Boolean = true
    var CMD_REQ_SW : Boolean = false

    private var dronepolyline: Polyline? = null

    var CountSendDroneLOC_IND : Int = 0
    var CountSendDroneLOC_gps : Int = 0
    var testag : Int = 0

    val dronelogv : String = "Drone Protocol Log"

    private lateinit var magframeLayout: FrameLayout
    private var initialX = 0f
    private var initialY = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    private val minSize = 200

    // lifecycle
    override fun onStart() {
        super.onStart()
        // USB 리시버 활성화
/*        val usbStatusFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbSerialReceiver, usbStatusFilter)*/

        // Bluetooth 리시버 활성화
        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        }
        registerReceiver(bluetoothReceiver, bluetoothFilter)
    }

    override fun onStop() {
        super.onStop()
        // 앱 종료시 AAT에 StopREQ 전송
        if(bluetoothSocket?.isConnected == true){
            sendStopREQ()
        }
        // USB Bluetooth 리시버 활성화 종료
        //unregisterReceiver(usbSerialReceiver)
        unregisterReceiver(bluetoothReceiver)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MainActivity xml 바인딩 및 선언
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        (supportFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment).getMapAsync(this)

        // USB 메니저 선언
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        usbHelper = UsbHelper(this, usbPermissionReceiver)

        // 버튼 이벤트

        /*
        RF 연결 방법
         1) 앱 실행 후 USB 연결
         2) 테스트용 RF 기준 CP2102 USB 엑세스 허용을 확인 누름
         3) 이후 RFConnect 버튼 눌러 실행
         */

        // RF USB 실행및 활성화

        binding.rfconnectbt.setOnClickListener {
            usbHelper!!.showUsbDeviceList { device: UsbDevice? ->
                this.onDeviceSelected(
                    device!!
                )
            }
        }

        /*
        AAT (Bluetooth) 연결 방법
         1) 앱 실행 후 BTconnect 실행
         2) 연결하고자 하는 블루투스 항목 선택
         3) Toast 통해 연결 디바이스의 MAC 확인
         */

        binding.btnTeststart.isInvisible = true
        binding.btnTestReset.isInvisible = true
        binding.btnTestcancel.isInvisible = true

        binding.btconnnectbt.setOnClickListener {
            //Toast.makeText(this, "btconnnectbt.setOnClickListener On", Toast.LENGTH_SHORT).show()
            //listenBT()
            if (bluetoothAdapter!!.isEnabled) { // 블루투스가 활성화 상태 (기기에 블루투스가 켜져있음)
                selectBluetoothDevice() // 블루투스 디바이스 선택 함수 호출
            }
        }

        /*
        AAT Test 용 Auto Fly 버튼
         */

        binding.autostartBT.setOnClickListener {
            val testdialog = TestCustomDialog(this, Testdronespeed,Testradius,TestradiusPoint) {
                spped, circleradius, circlepoint, circlestart, spin, btn ->
                Testdronespeed = spped
                Testradius = circleradius
                TestradiusPoint = circlepoint
                testag = circlestart
                testcricleSW = spin
                val testmessage = Testdronespeed.toString() + " / " + TestradiusPoint.toString()  + " / " + testag.toString() + " / " + testcricleSW.toString()
                if(btn){
                    //Toast.makeText(this,testmessage + " / circlebtn", Toast.LENGTH_SHORT).show()
                    var circlecenter = LatLng(AATlat,AATlong)
                    generateCirclePoints(circlecenter,Testradius,TestradiusPoint,testcricleSW)
                    testOnoff = true
                    binding.btnTeststart.isInvisible = false
                    binding.btnTestReset.isInvisible = false
                    binding.btnTestcancel.isInvisible = false
                }
                else{
                    //Toast.makeText(this, testmessage + " / custombtn", Toast.LENGTH_SHORT).show()
                    testOnoff = true
                    binding.btnTeststart.isInvisible = false
                    binding.btnTestReset.isInvisible = false
                    binding.btnTestcancel.isInvisible = false
                }
            }
            testdialog.show()

        }

        binding.btnTeststart.setOnClickListener {
            Autofly()
            testOnoff = false
        }

        binding.btnTestReset.setOnClickListener{
            Autoflyreset()
        }
        binding.btnTestcancel.setOnClickListener {
            Autoflyreset()
            binding.btnTeststart.isInvisible = true
            binding.btnTestReset.isInvisible = true
            binding.btnTestcancel.isInvisible = true
        }

        //binding.autoflyspeedET.setText(Testdronespeed.toString())

        // AAT 위치를 중앙으로하는 맵 이동
        binding.aatcenterBT.setOnClickListener {
            if(CMD_REQ_SW){
                sendDroneLOCIND(dronelat,dronelong,dronealt)
            }
            if(!Autoflyis){
                moveATTcenterMap(AATlat,AATlong)
                movemarker(dronelat,dronelong,AATlat,AATlong)
            }
        }

        binding.aatCmdReqBT.setOnClickListener{
            val testcmddialog = TestCmdreqDialog(this) {
                    index ->
                Toast.makeText(this, " CMDREQ SELETE BTN : " + index.toString(), Toast.LENGTH_SHORT).show()
                when (index) {
                    1 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    2 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    3 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    4 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    5 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    6 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    7 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    8 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                    9 -> sendCMDREQ(CMD_Command_AAT_Init_test)
                }
            }
            testcmddialog.show()


        }

        requestBluetoothPermissions()
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
                Toast.makeText(this, "권한 거부..", Toast.LENGTH_SHORT).show()
            }
        }

        providerClient = LocationServices.getFusedLocationProviderClient(this)
        apiClient = GoogleApiClient.Builder(this)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !== PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            )
        } else {
            apiClient.connect()
        }
    }
    // 최초 앱 실행시 Map 관련 초기화 작업 진행
    override fun onConnected(p0: Bundle?) {
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

    // Google Map 기본 인터페이스
    override fun onConnectionSuspended(p0: Int) {
    }

    // Google Map 기본 인터페이스
    override fun onMarkerDragStart(p0: Marker?) {
    }

    // Google Map 기본 인터페이스
    override fun onMarkerDrag(p0: Marker?) {
    }

    // 드론 이미지 드롭시 드론 이미지 위치 변경 및 AAT 좌표 전달 > 드론 테스트용 코드
    override fun onMarkerDragEnd(marker: Marker) {
        //googleMap?.clear()
        movemarker(marker.position.latitude,marker.position.longitude,AATlat,AATlong)
    }

    // Google Map 기본 인터페이스
    override fun onConnectionFailed(p0: ConnectionResult) {
    }


    // Google Map 동작 완료시 구글 맵 선언
    override fun onMapReady(p0: GoogleMap?) {
        googleMap = p0
        with(googleMap) {
            this?.setOnMarkerDragListener(this@MainActivity)
        }

        googleMap!!.uiSettings.isCompassEnabled = true

        googleMap!!.setOnMapClickListener { latLng ->
            if(testOnoff){
                addMarker(latLng)
            }
        }

        googleMap!!.setOnMarkerClickListener { marker ->
            if(testOnoff){
                removeMarker(marker)
            }
            true
        }
    }

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



    // sp를 px로 변환하는 함수
    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }

    private fun removeMarker(marker: Marker) {
        marker.remove() // 지도에서 제거
        markerList.remove(marker) // 리스트에서 제거
        redrawMarkers()
        drawPolyline()
    }

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


    private fun Autofly() {
        Log.d("Autofly", "Autofly 리스트 실행")
        var startpoint : LatLng? = null
        var endpoint : LatLng? = null
        Log.d("Autofly", "Autofly markerList 실행")
        automarkerList.clear()
         markerList.forEachIndexed { index, marker ->
            Log.d("MarkerList", "${index + 1}: (${marker.position.latitude}, ${marker.position.longitude})")
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
        Log.d(
            ContentValues.TAG,
            "Sent DroneLOC_IND / create automarkerList size  : " + automarkerList.size
        )
        autoflystatusUpdateTask = Runnable {autoflymatker()}
        autoflyhandler.post(autoflystatusUpdateTask!!)
    }

    private fun Autoflyreset() {
        googleMap!!.clear()
        markerList.clear()
        Autoflyis = false
        automarkerList.clear()
        if(autoflystatusUpdateTask != null){
            autoflyhandler.removeCallbacks(autoflystatusUpdateTask!!)
        }
        CountSendDroneLOC_IND = 0
        CountSendDroneLOC_gps = 0
    }

    private fun autoflymatker(){
        CountSendDroneLOC_IND = 0
        thread(start = true){
            run breaker@{
                automarkerList.forEachIndexed { index, latLng ->
                    sleep(1000)
                    if(Autoflyis) {
                        runOnUiThread {
                            if(!dronelat.equals(latLng.latitude))
                            {
                                ++CountSendDroneLOC_gps
                            }
                            dronelat = latLng.latitude
                            dronelong = latLng.longitude
                            dronealt = 350.0
                            updatedroneLogview(dronelat.toString(),dronelong.toString(),dronealt.toString())
                            movemarker(latLng.latitude, latLng.longitude, AATlat, AATlong)
                            if(!CMD_REQ_SW){
                                sendDroneLOCIND(dronelat,dronelong,dronealt)
                            }
                            if(index == automarkerList.size - 1){
                                CountSendDroneLOC_IND = 0
                                CountSendDroneLOC_gps = 0
                            }
                        }
                    }
                    else {
                        return@breaker
                    }
                }
            }
        }
    }

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
    private fun getBitmapDescriptorFactoryDrone(resId: Int): Bitmap? {
        var bitmap : Bitmap? = null
        val drawable = ResourcesCompat.getDrawable(resources, resId, null)
        if(drawable != null){
            bitmap = Bitmap.createBitmap(75,75, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0,0,canvas.width,canvas.height)
            drawable.draw(canvas)
        }
        return bitmap
    }

    // AAT 이미지 표시에 대한 함수
    private fun getBitmapDescriptorFactoryAnternna(resId: Int, anternnadeg: Float): Bitmap? {
        var bitmap : Bitmap? = null
        val drawable = ResourcesCompat.getDrawable(resources, resId, null)
        if(drawable != null){
            bitmap = Bitmap.createBitmap(150,150, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.rotate(anternnadeg,(canvas.width/2).toFloat(),(canvas.height/2).toFloat())
            drawable.setBounds(0,0,canvas.width,canvas.height)
            drawable.draw(canvas)
        }
        return bitmap
    }

    private fun moveATTcenterMap(latitude: Double, longitude: Double){
        val latLng = LatLng(latitude, longitude)

        val position : CameraPosition = CameraPosition.Builder()
            .target(latLng)
            .zoom(15f)
            .bearing(0f)
            .build() // 지도 중심 위치 이동
        googleMap!!.moveCamera(CameraUpdateFactory.newCameraPosition(position)) // 해당 좌표로 맵 이동
    }
    
    // RF 에서 드론 좌표값 변경시 마다 드론 이미지 및 AAT 이미지 갱신
    private fun movemarker(dronelatitude: Double, dronelongitude: Double, aatlatitude: Double, aatlongitude: Double){
        // 드론 좌표값 선언
        val dronelatLng = LatLng(dronelatitude, dronelongitude)
        // AAT 좌표값 선언
        val aatlatLng = LatLng(aatlatitude, aatlongitude)

        Log.d(ContentValues.TAG, "receive set Marker AATlat : " + aatlatitude + " AATlong : " + aatlongitude + " Dronelat : " + dronelatitude + " Dronelong : " + dronelongitude)

        // 드론 좌표와 AAT 좌표를 기반으로 안테나 트레커 각도를 계산
        val anternnadeg = getBearing(aatlatitude,aatlongitude,dronelatitude,dronelongitude)
        markerDrone?.remove()
        markeranternna?.remove()

        markerDrone = googleMap?.addMarker(
            MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromBitmap(getBitmapDescriptorFactoryDrone(R.drawable.drone)))
                    .position(dronelatLng)
                    .draggable(true)
                    .anchor(0.5F,0.5F)
                    .title("Drone")
        )


        markeranternna = googleMap?.addMarker(
            MarkerOptions()
                .icon(
                    BitmapDescriptorFactory.fromBitmap(getBitmapDescriptorFactoryAnternna(R.drawable.anternna_header,
                        abs((360F - anternnadeg.toFloat())) // 안테나 각도계산 기반으로 이미지 회전
                    )))
                .position(aatlatLng)
                .anchor(0.5f,0.5f)
                .title("Antenna")
        )
        dronepolyline?.remove()

        val polylineOptions = PolylineOptions().apply {
            color(0xFFFFFF00.toInt()) // 선 색상 (파란색)
            width(8f) // 선 두께
            add(markerDrone!!.position)
            add(markeranternna!!.position)
        }

        dronepolyline = googleMap?.addPolyline(polylineOptions) // 지도에 선 추가
    }

    fun ConvertDecimalDegreesToRadians(deg: Double) : Double{
        return (deg * PI / 180);
    }
    fun ConvertRadiansToDecimalDegrees(rad: Double) : Double{
        return (rad * 180 / PI);
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

        Log.d("MarkerList", "getDistanceMeterBetweenTwoLatLngCoordinate : " + (r * c).toInt())

        return r * c
    }

    fun calculatepointnum(betweendis : Int, speed : Int ): Int {
        Log.d("MarkerList", "calculatepointnum : " + ((betweendis.toInt() / speed) - 1))
        return ((betweendis.toInt() / speed) - 1)
    }

    private fun generateCirclePoints(center: LatLng, radius: Double, numPoints: Int, clockwise: Boolean){

        for (i in 0 until numPoints) {
            val theta = if (clockwise) {
                2.0 * Math.PI * (i + testag) / numPoints // 시계 방향 (0 → 360°)
            } else {
                2.0 * Math.PI * (numPoints - (i - testag)) / numPoints  // 반시계 방향 (360° → 0)
            }
            Log.d(ContentValues.TAG, "generateCirclePoints theta : $theta")
            //val theta = 2.0 * Math.PI * i / numPoints // 각도 계산
            val latOffset = (radius / 111111.0) * cos(theta) // 위도 오프셋
            val lngOffset = (radius / (111111.0 * cos(Math.toRadians(center.latitude)))) * sin(theta) // 경도 오프셋

            val lat = center.latitude + latOffset
            val lng = center.longitude + lngOffset

            addMarker(LatLng(lat,lng))
        }
        addMarker(markerList[0].position)
    }

    /*private fun generateCirclePoints(center: LatLng, radius: Double, numPoints: Int, clockwise: Boolean){

        for (i in 0 until numPoints) {
            val theta = if (clockwise) {
                2.0 * Math.PI * i / numPoints  // 시계 방향 (0 → 360°)
            } else {
                2.0 * Math.PI * (numPoints - i) / numPoints  // 반시계 방향 (360° → 0)
            }
            Log.d(ContentValues.TAG, "generateCirclePoints theta : $theta")
            //val theta = 2.0 * Math.PI * i / numPoints // 각도 계산
            val latOffset = (radius / 111111.0) * cos(theta) // 위도 오프셋
            val lngOffset = (radius / (111111.0 * cos(Math.toRadians(center.latitude)))) * sin(theta) // 경도 오프셋

            val lat = center.latitude + latOffset
            val lng = center.longitude + lngOffset

            addMarker(LatLng(lat,lng))
        }
        addMarker(markerList[0].position)
    }*/


    // connect fun

    // USB 시리얼 포트 리시버
/*    private val usbSerialReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    handleUsbDevice(intent)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    usbDevice = null
                    usbdevicename = ""
                    controllerUSB.terminateConnection()
                }
            }
        }
    }*/

    fun showAutoflyInputDialog(context: Context, onInputReceived: (String) -> Unit) {
        val editText = EditText(context)
        val dialog = AlertDialog.Builder(context)
            .setTitle("입력 창")
            .setMessage("값을 입력하세요:")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                onInputReceived(editText.text.toString())
            }
            .setNegativeButton("취소", null)
            .create()
        dialog.show()
    }

    private val usbPermissionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            onDeviceSelected(device)
                        } else {
                            Log.d("TAG", "Usb device null")
                        }
                    } else {
                        Log.d("TAG", "permission denied for device $device")
                    }
                }
            }
        }
    }

    // Bluetooth 리시버
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    //handleBluetoothDevice(intent)
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter == null) { // 디바이스가 블루투스를 지원하지 않을 때
                        Toast.makeText(applicationContext, "블루투스 미지원 기기입니다.", Toast.LENGTH_LONG)
                            .show()
                    } else { // 디바이스가 블루투스를 지원 할 때

                        if (bluetoothAdapter!!.isEnabled) { // 블루투스가 활성화 상태 (기기에 블루투스가 켜져있음)
                        } else { // 블루투스가 비 활성화 상태 (기기에 블루투스가 꺼져있음)

                            // 블루투스를 활성화 하기 위한 다이얼로그 출력
                            // 선택한 값이 onActivityResult 함수에서 콜백
                            startActivityForResult(intent, 1)
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    controllerBT.terminateConnection()
                }
            }
        }
    }

    // 시리얼 포트 연결 시도
    private fun tryConnectToSerial() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                if (usbDevice == null) return@launch
                if (controllerSerial.connectTo(usbDevice!!)) {
                    onDeviceSelected(usbDevice!!)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 시리얼 포트 연결 이후 디바이스 오픈 및 최초 통신 진행
    private fun onDeviceSelected(device: UsbDevice) {
        val connection = usbManager!!.openDevice(device)
        if (connection != null) { // USB connect 가 실행되었다면
            val serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection)
            if (serialDevice != null) {
                if (serialDevice.open()) { // 시리얼 포트 오픈
                    serialDevice.setBaudRate(USBBaudrate) // 보레이트 설정
                    try {
                        val pipedOut = PipedOutputStream()
                        val pipedIn = PipedInputStream(pipedOut) // pip 선언

                        serialDevice.read { data: ByteArray? ->
                            try {
                                pipedOut.write(data)
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }

                        val mavlinkConnection =
                            MavlinkConnection.create(pipedIn, UsbSerialOutputStream(serialDevice)) // Mavlink 선언
                        mavlinkDataProcessor = MavlinkDataProcessor(mavlinkConnection) // Mavlink Connect 실행
                        mavlinkDataProcessor!!.requestRawImuData(24) // IMU GPS_RAW_INT (24) 요청
                        mavlinkDataProcessor!!.requestRawImuData(27) // IMU RAW_IMU (27) 요청
                        // Mavlink Message 리스터 설정
                        mavlinkDataProcessor!!.startMavlinkMessageListener { message: MavlinkMessage<*>? ->
                            this.processMAVLinkData(
                                message!!
                            )
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
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
                Log.d("TAG", "imuMessage / magX : " + imuMessage.xmag().toString() + " magY : " + imuMessage.ymag().toString() + " magZ : " + imuMessage.zmag().toString())

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
                updatedroneLogview(dronelat.toString(),dronelong.toString(),dronealt.toString())
                if (bluetoothSocket?.isConnected == true){
                    sendDroneLOCIND(dronelat,dronelong,dronealt)
                }
                //googleMap?.clear()
                //movemarker(dronelat,dronelong,AATlat,AATlong)
            }
        }
    }
    // USB 디바이스 선택 및 실행

    //Bluetooth 권한 확인
    private fun requestBluetoothPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
            Toast.makeText(applicationContext, "먼저 Bluetooth 설정에 들어가 페어링 해주세요", Toast.LENGTH_SHORT)
                .show()
        } else {
            // 디바이스를 선택하기 위한 다이얼로그 생성
            val builder = AlertDialog.Builder(this)
            builder.setTitle("페어링 되어있는 블루투스 디바이스 목록")
            // 페어링 된 각각의 디바이스의 이름과 주소를 저장
            val list: MutableList<String> = ArrayList()

            // 모든 디바이스의 이름을 리스트에 추가
            for (bluetoothDevice in devices!!) {
                list.add(bluetoothDevice.name)
            }
            list.add("취소")

            // List를 CharSequence 배열로 변경
            val charSequences = list.toTypedArray<CharSequence>()
            list.toTypedArray<CharSequence>()

            // 해당 아이템을 눌렀을 때 호출 되는 이벤트 리스너
            builder.setItems(
                charSequences
            ) { dialog, which -> // 해당 디바이스와 연결하는 함수 호출
                connectBluetooth(charSequences[which].toString())
            }

            // 뒤로가기 버튼 누를 때 창이 안닫히도록 설정
            builder.setCancelable(false)

            // 다이얼로그 생성
            val alertDialog = builder.create()
            alertDialog.show()
        }
    }

    private fun connectBluetooth(deviceName: String) {
        // 페어링 된 디바이스들을 모두 탐색

        for (tempDevice in devices!!) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            // 사용자가 선택한 이름과 같은 디바이스로 설정하고 반복문 종료
            if (deviceName == tempDevice.name) {
                bluetoothDevice = tempDevice
                break
            }
        }
        Toast.makeText(this, bluetoothDevice.toString() + " 연결 완료", Toast.LENGTH_SHORT).show()

        // UUID 생성
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        // Rfcomm 채널을 통해 블루투스 디바이스와 통신하는 소켓 생성
        try {
            bluetoothSocket = bluetoothDevice!!.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket!!.connect()
            outputStream = bluetoothSocket!!.outputStream
            inputStream = bluetoothSocket!!.inputStream

            // 데이터 송수신 함수 호출
            sendStartREQ()
            startListeningForMessages()
            //startReceivingData(controllerBT)

        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(ContentValues.TAG, "Bluetooth connection failed", e)
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
            updateLogView("sendStartREQ",bytesToHex(startREQ))
            Log.d(dronelogv, "Sent StartREQ - " + bytesToHex(startREQ))
        } catch (e: IOException) {
            Log.e(dronelogv, "Error sending StartREQ", e)
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
        memcpy(stopREQ, 2, App_ID, 0, 4) // App_ID
        stopREQ[6] = 0x0A.toByte() // Request
        stopREQ[7] = 0xFF.toByte() // Checksum
        stopREQ[8] = 0x55.toByte() // ETX

        // StopREQ protocol 전달
        try {
            outputStream!!.write(stopREQ)
            updateLogView("sendStopREQ",bytesToHex(stopREQ))
            Log.d(dronelogv, "Sent StopREQ - " + bytesToHex(stopREQ))
        } catch (e: IOException) {
            Log.e(dronelogv, "Error sending StopREQ", e)
            throw e
        }
    }

    // AAT StopREQ 전달 문
    @Throws(IOException::class)
    private fun sendCMDREQ(Command : ByteArray) {
        // AAT 전달용 sendCMDREQ 생성
        val CMDREQ = ByteArray(10)
        CMDREQ[0] = 0xAA.toByte() // STX
        CMDREQ[1] = 0x06.toByte() // LEN
        memcpy(CMDREQ, 2, AAT_REQ_ID, 0, 4) // App_ID
        memcpy(CMDREQ, 6, Command, 0, 2) // AAT_ID
        CMDREQ[8] = 0xFF.toByte() // Checksum
        CMDREQ[9] = 0x55.toByte() // ETX

        // sendCMDREQ protocol 전달
        try {
            outputStream!!.write(CMDREQ)
            updateLogView("sendCMDREQ",bytesToHex(CMDREQ))
            Log.d(dronelogv, "Sent sendCMDREQ - " + bytesToHex(CMDREQ))
            CMD_REQ_SW = true
        } catch (e: IOException) {
            Log.e(dronelogv, "Error sending sendCMDREQ", e)
            throw e
        }
    }

    // RF 위도 경도 고도 AAT 전달
    private fun sendDroneLOCIND(drone_lat: Double, drone_long: Double, drone_alt: Double) {
        // Example GPS data
        /*double droneLatitude = 37.8744341;
        double droneLongitude = 127.1566792;
        float droneAltitude = 264.34f;*/

        val droneLatitudeIntPart: Long = drone_lat.toLong() //드론 Lat 값 선언 및 형 변환
        val droneLatitudeFracPart: Long =
            ((drone_lat - droneLatitudeIntPart) * 10000000.0).toLong() //(long)((droneLatitude - droneLatitudeIntPart) * 10000000.0);
        val droneLongitudeIntPart: Long = drone_long.toLong() // 드론 Long 선언 및 형 변환
        val droneLongitudeFracPart: Long =
            ((drone_long - droneLongitudeIntPart) * 10000000.0).toLong() //(long)((droneLongitude - droneLongitudeIntPart) * 10000000.0);


        // AAT 전달용 DroneLOCIND 생성
        val gpsData = ByteArray(29)
        gpsData[0] = 0xAA.toByte() // STX
        gpsData[1] = 0x19.toByte() // LEN
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
        gpsData[26] = 0x0F.toByte() // Drone status
        gpsData[27] = 0xFF.toByte() // Checksum
        gpsData[28] = 0x55.toByte() // ETX

        // AAT 전달용 DroneLOCIND protocol 전달
        try {
            outputStream!!.write(gpsData)
            updateLogView("sendDroneLOCIND",bytesToHex(gpsData))
            ++CountSendDroneLOC_IND
            Log.d(
                dronelogv,
                "Sent DroneLOC_IND / Count : " + CountSendDroneLOC_IND.toString() + " / GPSupdatecount : " + CountSendDroneLOC_gps.toString() + " / LatInt = " + droneLatitudeIntPart + " / LatFrac = " + droneLatitudeFracPart + " / LonInt = " + droneLongitudeIntPart + " / LonFrac = " + droneLongitudeFracPart
            )
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(dronelogv, "Error sending DroneLOC_IND", e)
        }
    }

    // Bluetooth 메시지 확인용 thread
    private fun startListeningForMessages() {
        Log.d(ContentValues.TAG, "Start startListeningForMessages")
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (true) {
                try {
                    bytes = inputStream!!.read(buffer)
                    Log.d(dronelogv, "Start startListeningForMessages handler : $bytes")
                    if (bytes > 0) {
                        handler.post(Runnable {receiveREQ(buffer)})
                        Log.d(dronelogv, "Start startListeningForMessages handler")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e(dronelogv, "Error reading from Bluetooth", e)
                    break
                }
            }
        }.start()
    }

    // AAT REQ 데이터 확인용 함수
    fun receiveREQ(buffer: ByteArray) {
        Log.d(ContentValues.TAG, "packet " + bytesToHex(buffer))
        val endIndex = buffer.indexOf(0x55.toByte())
        val slicebuffer = if (endIndex != -1 && buffer[endIndex-1] == 0x00.toByte()) buffer.copyOfRange(0,endIndex) else buffer
        updateLogView("receiveREQ",bytesToHex(slicebuffer))
        if (buffer[0] == 0xAA.toByte() && buffer[1] == 0x1A.toByte()) {
            Log.d(ContentValues.TAG, "Receive test Drone_Loc_Req from AAT")
            // Parse the AAT_GPS coordinates
            // AAT 데이터 파싱
            AAT_REQ_ID = byteArrayOf(buffer[2],buffer[3],buffer[4],buffer[5])
            val aatLatIntPart = bytesToLong(buffer, 6)
            val aatLatFracPart = bytesToLong(buffer, 10)
            val aatLonIntPart = bytesToLong(buffer, 14)
            val aatLonFracPart = bytesToLong(buffer, 18)
            AATlat = aatLatIntPart + (aatLatFracPart / 1e7) // AAT lat 값 파싱 및 변환
            AATlong = aatLonIntPart + (aatLonFracPart / 1e7) // AAT long 값 파싱 및 변환
            //AATlat = bytesToFloat(buffer, 22)
            updateaatLogview(AATlat.toString(),AATlong.toString(),AATalt.toString())
            Log.d(dronelogv, "receive AAT ID: " + bytesToHex(AAT_REQ_ID))
            Log.d(dronelogv, "receive AAT GPS lat : " + AATlat + " long : " + AATlong)
            //AAT_Latitude = aatLatIntPart + (aatLatFracPart / 1e7)
            //AAT_Longitude = aatLonIntPart + (aatLonFracPart / 1e7)

            /*long latitudeFixedPoint = bytesToLong(buffer, 6);
            long longitudeFixedPoint = bytesToLong(buffer, 14);
            AAT_Latitude = latitudeFixedPoint / 100000000.0;
            AAT_Longitude = longitudeFixedPoint / 100000000.0;*/
            //AAT_Altitude = bytesToFloat(buffer, 22)

            // AAT 상태값 파싱 및 출력
            val status = buffer[26]
            val statustest = bytesToHex(buffer,26,2)
            Log.d(dronelogv, "Receive test Drone_Loc_Req data : " + statustest)
            var statusStr = ""
            when (status) {
                0x00.toByte() -> statusStr = "OK"
                0xAA.toByte() -> statusStr = "AAT not ready"
                0xBB.toByte() -> statusStr = "GPS inactive"
                0xCC.toByte() -> statusStr = "Lost target"
                0xDB.toByte() -> statusStr = "AAT Battery Low"
            }
            //updateLogView(statusStr)

            //sendDroneLOCIND(dronelat,dronelong,dronealt)
            Log.d(dronelogv, "Send Drone_Loc_Req from APP")

        } else if(buffer[0] == 0xAA.toByte() && buffer[1] == 0x0A.toByte()){

            Log.d(dronelogv, "AAT_CMD_IND packet " + bytesToHex(buffer))
            val value = bytesToHex(buffer,10,2)
            Log.d(dronelogv, "AAT_CMD_IND AAT Status packet " + value)
            CMD_REQ_SW = false
        }
        else if (buffer[0] == 0xAA.toByte() && buffer[1] == 0x05.toByte() && buffer[6] == 0x0B.toByte()) {
            // Handle stop acknowledgment
            stopCommunication()
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
        Log.d(ContentValues.TAG, "Bytes to Long (HEX): $hexString")

        return value
    }

    private fun stopCommunication() {
        try {
            if (bluetoothSocket != null) {
                sendStopREQ()
                bluetoothSocket!!.close()
            }
            handler.removeCallbacks(statusUpdateTask!!)
            Log.d(ContentValues.TAG, "Stopped communication")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(ContentValues.TAG, "Error stopping communication", e)
        }
    }
    // Messagebox
    private fun updateLogView(send: String , message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val localDataTime : LocalDateTime = LocalDateTime.now()
            binding.tvLog.text = "${binding.tvLog.text}$localDataTime : [$send] - $message\n"

        }
    }

    private fun updatedroneLogview(lat : String, long : String, alt : String) {
        binding.tvMasboxDronelat.text = "lat : " + String.format("%.7f", lat.toDouble())
        binding.tvMasboxDronelong.text = "lon : " + String.format("%.7f",long.toDouble())
        binding.tvMasboxDronealt.text = "alt : " + String.format("%.1f",alt.toDouble())

    }

    private fun updateaatLogview(lat : String, long : String, alt : String) {
        binding.tvMasboxAatllat.text = "lat : " + lat
        binding.tvMasboxAatlong.text = "lon : " + long
        binding.tvMasboxAatalt.text = "alt : " + alt

    }
}