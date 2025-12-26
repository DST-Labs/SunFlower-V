package com.dstl.sunflower

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import connector.login.ApiClient
import connector.login.LoginRequest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var idInput: EditText
    private lateinit var pwInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var version_tv: TextView

    companion object {
        private const val MAX_LEN = 32
    }
    var LoginLog : String = "LoginLog"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        ApiClient.init(this)

        Logger.writeLog(LoginLog, "LoginActivity Start!")

        idInput = findViewById(R.id.usernameEdit)
        pwInput = findViewById(R.id.passwordEdit)
        loginBtn = findViewById(R.id.loginBtn)
        version_tv = findViewById(R.id.version_tv)


        // ✅ 입력 자체를 32자로 제한
        idInput.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN))
        pwInput.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN))

        version_tv.text = "Version 1.0"
        idInput.setText("test01")
        pwInput.setText("qwer1234!")

        loginBtn.setOnClickListener {
            val id = idInput.text.toString().trim()
            val pw = pwInput.text.toString()


            if (id.isEmpty() || pw.isEmpty()) {
                Logger.writeLog(LoginLog, "아이디/비밀번호를 입력하세요.")
                Toast.makeText(this, "아이디/비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (id.length > MAX_LEN || pw.length > MAX_LEN) {
                Logger.writeLog(LoginLog, "아이디/비밀번호는 최대 32자까지 입력 가능합니다.")
                Toast.makeText(this, "아이디/비밀번호는 최대 32자까지 입력 가능합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val response = ApiClient.authApi.login(LoginRequest(id, pw))

                    if (response.isSuccessful) {
                        val token = response.body()!!.access_token
                        saveToken(token)

                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        // ❌ 로그인 실패 → 비밀번호 삭제
                        pwInput.text.clear()
                        pwInput.requestFocus()
                        Logger.writeLog(LoginLog, "로그인 실패")
                        Toast.makeText(this@LoginActivity, "로그인 실패", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // ❌ 서버/네트워크 오류 → 비밀번호 삭제
                    pwInput.text.clear()
                    pwInput.requestFocus()
                    Logger.writeLog(LoginLog, "서버 연결 오류")
                    Toast.makeText(this@LoginActivity, "서버 연결 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveToken(token: String) {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        prefs.edit().putString("token", token).apply()
    }
}
