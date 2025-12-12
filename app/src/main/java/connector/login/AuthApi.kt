package connector.login

import retrofit2.Response
import retrofit2.http.*

interface AuthApi {

    @POST("/auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<Map<String, String>>

    @POST("/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<TokenResponse>

    @GET("/auth/me")
    suspend fun me(@Header("Authorization") token: String): Response<Map<String, Any>>
}