package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun generateText(prompt: String, systemInstruction: String? = null, jsonOutput: Boolean = false): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured in the Secrets panel.")
        }

        val content = GeminiContent(listOf(GeminiPart(text = prompt)))
        val instruction = systemInstruction?.let { GeminiContent(listOf(GeminiPart(text = it))) }
        val config = if (jsonOutput) GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.4) else null

        val request = GeminiRequest(
            contents = listOf(content),
            generationConfig = config,
            systemInstruction = instruction
        )

        val response = service.generateContent(apiKey, request)
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Received an empty response from Gemini.")
    }

    suspend fun analyzeImage(bitmap: Bitmap, prompt: String, systemInstruction: String? = null): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured in the Secrets panel.")
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val parts = listOf(
            GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image)),
            GeminiPart(text = prompt)
        )
        val content = GeminiContent(parts)
        val instruction = systemInstruction?.let { GeminiContent(listOf(GeminiPart(text = it))) }

        val request = GeminiRequest(
            contents = listOf(content),
            systemInstruction = instruction
        )

        val response = service.generateContent(apiKey, request)
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Received an empty response from Gemini Vision.")
    }
}
