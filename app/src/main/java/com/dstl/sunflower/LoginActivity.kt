package com.dstl.sunflower

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var idInput: EditText
    private lateinit var pwInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var versionTv: TextView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    companion object {
        private const val MAX_LEN = 32
        private const val LOG_TAG = "LoginLog"

        // ✅ 배포 Hosting 경유 (firebase.json: /api/** -> backend)
        private const val LOGIN_URL =
            "https://us-central1-sumflower-a67e3.cloudfunctions.net/backend/auth/login"
    }

    // OkHttp 재사용
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Log.d(LOG_TAG, "LoginActivity Start!")

        val opts = com.google.firebase.FirebaseApp.getInstance().options
        Log.d(LOG_TAG, "Firebase projectId=${opts.projectId} appId=${opts.applicationId}")

        idInput = findViewById(R.id.usernameEdit)
        pwInput = findViewById(R.id.passwordEdit)
        loginBtn = findViewById(R.id.loginBtn)
        versionTv = findViewById(R.id.version_tv)

        idInput.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN))
        pwInput.filters = arrayOf(InputFilter.LengthFilter(MAX_LEN))

        versionTv.text = "Sunflower V1.0"

        // 테스트값(원하면 삭제)
        idInput.setText("user001")
        pwInput.setText("qwer1234!")

        loginBtn.setOnClickListener {
            val username = idInput.text.toString().trim()
            val pw = pwInput.text.toString()

            if (username.isEmpty() || pw.isEmpty()) {
                Log.d(LOG_TAG, "아이디/비밀번호를 입력하세요.")
                toast("아이디/비밀번호를 입력하세요.")
                return@setOnClickListener
            }
            if (username.length > MAX_LEN || pw.length > MAX_LEN) {
                Log.d(LOG_TAG, "아이디/비밀번호는 최대 32자까지 입력 가능합니다.")
                toast("아이디/비밀번호는 최대 32자까지 입력 가능합니다.")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                loginBtn.isEnabled = false
                try {
                    Log.d(LOG_TAG, "HTTP login start username=$username url=$LOGIN_URL")

                    // ✅ 네트워크는 IO 스레드에서
                    val resp = withContext(Dispatchers.IO) {
                        httpLogin(username, pw)
                    }

                    if (!resp.ok || resp.customToken.isBlank()) {
                        Log.d(LOG_TAG, "HTTP login bad response: ${resp.raw}")
                        pwInput.text.clear()
                        pwInput.requestFocus()
                        toast(resp.userMessage ?: "로그인 실패")
                        return@launch
                    }

                    // ✅ customToken 으로 FirebaseAuth 로그인 (그대로 유지)
                    auth.signInWithCustomToken(resp.customToken).await()

                    Log.d(LOG_TAG, "Firebase customToken 로그인 성공: $username")

                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()

                } catch (e: Exception) {
                    Log.d(LOG_TAG, "Login FAIL: ${e.message}")
                    pwInput.text.clear()
                    pwInput.requestFocus()
                    toast("로그인 실패")
                } finally {
                    loginBtn.isEnabled = true
                }
            }
        }
    }

    private data class HttpLoginResult(
        val ok: Boolean,
        val uid: String,
        val customToken: String,
        val userMessage: String?,
        val raw: String
    )

    /**
     * ✅ POST https://sumflower-a67e3.web.app/api/auth/login
     * Body: {"username":"...","password":"..."}
     * Success: { ok:true, uid:"...", customToken:"..." }
     * Fail: { error:"user_not_found" | "invalid_credentials" | "locked" | "disabled" | ... , failedCount?, lockedUntil? }
     */
    private fun httpLogin(username: String, password: String): HttpLoginResult {
        val json = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val req = Request.Builder()
            .url(LOGIN_URL)
            .post(body)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            val obj = runCatching { JSONObject(raw) }.getOrNull()

            if (!res.isSuccessful) {
                val msg = buildFailMessage(obj, res.code)
                return HttpLoginResult(
                    ok = false,
                    uid = "",
                    customToken = "",
                    userMessage = msg,
                    raw = "HTTP ${res.code} $raw"
                )
            }

            val ok = obj?.optBoolean("ok", false) ?: false
            val uid = obj?.optString("uid", "") ?: ""
            val token = obj?.optString("customToken", "") ?: ""

            return HttpLoginResult(
                ok = ok,
                uid = uid,
                customToken = token,
                userMessage = null,
                raw = raw
            )
        }
    }

    private fun buildFailMessage(obj: JSONObject?, httpCode: Int): String {
        val err = obj?.optString("error", "") ?: ""
        val failedCount = obj?.optInt("failedCount", -1) ?: -1
        val lockedUntil = obj?.optLong("lockedUntil", -1L) ?: -1L

        return when {
            err == "disabled" -> "비활성화된 계정입니다."
            err == "locked" -> {
                if (lockedUntil > 0) {
                    val remainSec = ((lockedUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    "계정이 잠겼습니다. 약 ${remainSec / 60}분 후 재시도"
                } else "계정이 잠겼습니다."
            }
            err == "user_not_found" -> "존재하지 않는 계정입니다."
            err == "invalid_credentials" -> {
                if (failedCount >= 0) "아이디/비밀번호가 올바르지 않습니다. (실패 $failedCount/5)"
                else "아이디/비밀번호가 올바르지 않습니다."
            }
            err.isNotBlank() -> "로그인 실패: $err"
            else -> "로그인 실패 (HTTP $httpCode)"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
