package com.elysium369.meet.ai.providers.openai_compatible

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object OpenAiCompatibleClient {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = false
    }

    val aiHttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        
        install(ContentNegotiation) {
            json(OpenAiCompatibleClient.json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000L
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 60_000L
        }

        install(DefaultRequest) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
    }
}
