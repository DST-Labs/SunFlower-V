package com.dstl.sunflower
import android.app.Application
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class Logger : Application() {

    companion object {
        private var logWriter: FileWriter? = null

        // 로그 작성 함수 (타이틀 + 내용)
        fun writeLog(title: String, content: String) {
            try {
                val timestamp = SimpleDateFormat("yyyy:MM:dd-HH:mm:ss", Locale.getDefault()).format(Date())
                logWriter?.apply {
                    write("[$timestamp][$title] $content\n")
                    flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initLogFile()
    }

    override fun onTerminate() {
        super.onTerminate()
        closeLogFile()
    }

    // 파일 초기화
    private fun initLogFile() {
        try {
            val dir = File(getExternalFilesDir(null).toString() + "/Logs")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "Sunflower_log_${SimpleDateFormat("yyyy:MM:dd-HH:mm:ss", Locale.getDefault()).format(Date())}.txt"
            val file = File(dir, fileName)

            logWriter = FileWriter(file, true)  // 이어쓰기 모드
            writeLog("Sunflower", "Application started")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 파일 닫기
    private fun closeLogFile() {
        try {
            writeLog("Sunflower", "Application terminated")
            logWriter?.close()
            logWriter = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}