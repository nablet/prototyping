package com.cloudstaff.myapplication.utils.retrofit


import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

val token = "app-RUajyaiMG0aoPHM4bWRMpNTW"
val workflowToken = "app-HRGp81c9ORQSH6ZFeJIjlPAF"

val client = OkHttpClient.Builder()
	.addInterceptor(HttpLoggingInterceptor().apply {
		setLevel(HttpLoggingInterceptor.Level.BODY)
	})
	.connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
	.readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
	.writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
	.build()

val retrofit = Retrofit.Builder()
	.baseUrl("https://dify-hackaton.cloudstaff.io/") // base URL
	.client(client)
	.addConverterFactory(GsonConverterFactory.create())
	.build()

val api = retrofit.create(ApiService::class.java)



interface ApiService {

	@Headers("Content-Type: application/json")
	@POST("v1/workflows/run")
	suspend fun postData(
		@Header("Authorization") token: String,
		@Body payload: Payload
	): DifyResponse

    @Multipart
    @POST("v1/files/upload")
    suspend fun uploadFile(
		@Header("Authorization") token: String,
        @Part file: okhttp3.MultipartBody.Part
    ): UploadFileResponse

    @Headers("Content-Type: application/json")
    @POST("v1/workflows/8c94e679-bcf0-4777-b38a-2c3bb0640d85/run")
    suspend fun runWorkflow(
		@Header("Authorization") token: String,
        @Body payload: WorkflowPayload
    ): WorkflowResponse
}

data class Inputs(
	val type: String,
	val barangay: String,
	val city: String,
	val coordinate: String
)

data class Payload(
	val inputs: Inputs,
	val user: String = "erwinf-user-1"
)

@Serializable
data class DifyResponse(
	val data: DifyData,
)
@Serializable
data class DifyData(
	val outputs: Area,
)
@Serializable
data class Area(
	val area: String,
)

@Serializable
data class PointsOfInterest(
	val nearest_evacuations: List<Locations>? = null,
	val nearest_relief_goods: List<Locations>? = null,
	val nearest_hospitals: List<Locations>? = null,
)
@Serializable
data class Locations(
	val name: String? = null,
	val building: String? = null,
	val address: String,
	val coordinates: Coordinates,
	val distance_km: Double,
)
@Serializable
data class Coordinates(
	val lat: Double,
	val lng: Double,
)

@Serializable
data class UploadFileResponse(
    val id: String,
    val name: String,
    val size: Long,
    val extension: String,
    val mime_type: String,
    val created_by: String,
    val created_at: Long,
    val preview_url: String? = null,
    val source_url: String
)

@Serializable
data class WorkflowResponse(
    val task_id: String,
    val workflow_run_id: String,
    val data: WorkflowData
)

@Serializable
data class WorkflowData(
    val id: String,
    val workflow_id: String,
    val status: String,
    val outputs: WorkflowOutputs,
    val error: String? = null,
    val elapsed_time: Double,
    val total_tokens: Int,
    val total_steps: Int,
    val created_at: Long,
    val finished_at: Long
)

@Serializable
data class WorkflowOutputs(
    val result: String
)


data class WorkflowPayload(
    val user: String = "erwinf-user-1",
    val inputs: WorkflowInputs
)

data class WorkflowInputs(
    val picture: WorkflowPicture
)

data class WorkflowPicture(
    val type: String = "image",
    val transfer_method: String = "local_file",
    val upload_file_id: String
)