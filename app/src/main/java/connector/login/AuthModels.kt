package connector.login

data class RegisterRequest(
    val username: String,
    val password: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class TokenResponse(
    val access_token: String,
    val token_type: String
)
data class UserDto(
    val id: Int,
    val username: String,
    val is_admin: Int,
    val is_active: Int,
    val created_at: String
)

data class AuthLogDto(
    val id: Int,
    val event: String,
    val username: String?,
    val ip: String?,
    val user_agent: String?,
    val ok: Int,
    val detail: String?,
    val created_at: String
)

data class AdminCreateUserRequest(
    val username: String,
    val password: String,
    val is_admin: Boolean = false
)

data class AdminSetActiveRequest(
    val active: Boolean
)