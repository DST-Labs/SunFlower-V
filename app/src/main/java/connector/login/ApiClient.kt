package connector.login

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "http://www.kimmans11.com:18000"  // NAS IP로 변경

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private class AuthInterceptor(private val context: android.content.Context) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            val token = context.getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
                .getString("token", null)

            val req = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            return chain.proceed(req)
        }
    }

    // ✅ Application Context를 주입받아야 해서 init 필요
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private val client: OkHttpClient by lazy {
        val b = OkHttpClient.Builder()
            .addInterceptor(logging)

        appContext?.let { b.addInterceptor(AuthInterceptor(it)) }

        b.build()
    }

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}