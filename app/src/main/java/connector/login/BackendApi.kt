package connector.login

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BackendApi {
    @POST("auth/login")
    suspend fun login(
        @Header("Authorization") bearer: String,
        @Body body: LoginRequest
    ): LoginResponse
}