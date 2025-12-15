package com.dstl.sunflower

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        ApiClient.init(this)

        idInput = findViewById(R.id.usernameEdit)
        pwInput = findViewById(R.id.passwordEdit)
        loginBtn = findViewById(R.id.loginBtn)

        loginBtn.setOnClickListener {
            val id = idInput.text.toString().trim()
            val pw = pwInput.text.toString().trim()

            lifecycleScope.launch {
                try {
                    val response = ApiClient.authApi.login(
                        LoginRequest(id, pw)
                    )

                    if (response.isSuccessful) {
                        val token = response.body()!!.access_token

                        // ✅ (선택) 토큰 저장
                        saveToken(token)

                        // ✅ MainActivity로 이동
                        val intent = Intent(
                            this@LoginActivity,
                            MainActivity::class.java
                        )
                        startActivity(intent)
                        finish() // LoginActivity 제거

                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "로그인 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@LoginActivity,
                        "서버 연결 오류",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveToken(token: String) {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        prefs.edit().putString("token", token).apply()
    }
}
