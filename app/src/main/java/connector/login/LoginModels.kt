package connector.login

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("customToken") val customToken: String? = null,

    // 에러 케이스(서버가 내려주는 형태와 맞춤)
    @SerializedName("error") val error: String? = null,
    @SerializedName("failedCount") val failedCount: Int? = null,
    @SerializedName("lockedUntil") val lockedUntil: Long? = null
)



