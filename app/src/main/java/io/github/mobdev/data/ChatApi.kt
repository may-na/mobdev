package io.github.mobdev.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @POST("login")
    suspend fun login(@Body req: LoginRequestDto): Response<String>

    @POST("logout")
    suspend fun logout(@Header("X-Auth-Token") token: String): Response<Unit>

    @GET("channels")
    suspend fun channels(@Header("X-Auth-Token") token: String): Response<List<String>>

    @GET("channel/{name}")
    suspend fun messages(
        @Header("X-Auth-Token") token: String,
        @Path("name") name: String,
        @Query("limit") limit: Int,
        @Query("lastKnownId") lastKnownId: Long,
        @Query("reverse") reverse: Boolean,
    ): Response<List<MessageDto>>

    @POST("messages")
    suspend fun send(
        @Header("X-Auth-Token") token: String,
        @Body msg: SendMessageDto,
    ): Response<String>
}
