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

    @GET("/admin/api/users")
    suspend fun me(): Response<Map<String, Any>>

    @GET("/admin/api/logs")
    suspend fun adminLogs(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 200
    ): Response<List<AuthLogDto>>

    @POST("/admin/api/users")
    suspend fun adminCreateUser(
        @Header("Authorization") token: String,
        @Body req: AdminCreateUserRequest
    ): Response<Map<String, String>>

    @PATCH("/admin/api/users/{userId}/active")
    suspend fun adminSetActive(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int,
        @Body req: AdminSetActiveRequest
    ): Response<Map<String, String>>
}