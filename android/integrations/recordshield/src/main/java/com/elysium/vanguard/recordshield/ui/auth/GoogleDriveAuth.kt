package com.elysium.vanguard.recordshield.ui.auth

import android.content.Context
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.elysium.vanguard.recordshield.data.cloud.GoogleDriveClient
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * GoogleDriveAuth — Google Sign-In + OAuth2 Token Exchange
 *
 * Flow:
 *   1. User taps "Connect with Google" → Google account picker opens
 *   2. User selects account → returns authCode (via requestServerAuthCode)
 *   3. App exchanges authCode for access_token + refresh_token via HTTP POST
 *   4. Tokens saved to EncryptedSharedPreferences
 *   5. access_token used as Bearer for Drive REST API calls
 */
object GoogleDriveAuth {

    private const val TAG = "GoogleDriveAuth"
    private val DRIVE_SCOPE = Scope("https://www.googleapis.com/auth/drive.file")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = HttpClient(OkHttp) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(json)
        }
    }

    /**
     * Create a GoogleSignInClient configured for Drive access.
     * Uses requestServerAuthCode to get an auth code we can exchange for access_token.
     */
    fun getSignInClient(context: Context): GoogleSignInClient {
        val webClientId = getWebClientId(context)
        Log.i(TAG, "Creating GoogleSignInClient with webClientId: ${webClientId.take(20)}...")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DRIVE_SCOPE)
            .requestServerAuthCode(webClientId, false)
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Handle the sign-in result and extract the auth code.
     */
    fun handleSignInResult(
        task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>
    ): com.google.android.gms.auth.api.signin.GoogleSignInAccount? {
        return try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                Log.i(TAG, "Google Sign-In successful: ${account.email}")
                Log.i(TAG, "ServerAuthCode available: ${account.serverAuthCode != null}")
                account
            } else {
                Log.e(TAG, "Sign-in returned null account")
                null
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            Log.e(TAG, "Google Sign-In failed: statusCode=${e.statusCode}", e)
            null
        }
    }

    /**
     * Exchange auth code for access_token + refresh_token.
     * This is the critical step — the auth code from Google Sign-In is exchanged
     * for real OAuth2 tokens that work with the Drive REST API.
     */
    suspend fun exchangeAuthCodeForTokens(
        authCode: String,
        context: Context
    ): TokenResult? {
        val webClientId = getWebClientId(context)
        val webClientSecret = getWebClientSecret(context)

        Log.i(TAG, "Exchanging auth code for tokens...")

        return try {
            val response: HttpResponse = httpClient.post("https://oauth2.googleapis.com/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(FormDataContent(Parameters.build {
                    append("code", authCode)
                    append("client_id", webClientId)
                    append("client_secret", webClientSecret)
                    append("redirect_uri", "") // Not used for installed apps
                    append("grant_type", "authorization_code")
                }))
            }

            if (response.status == HttpStatusCode.OK) {
                val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
                val accessToken = body["access_token"]?.jsonPrimitive?.content
                val refreshToken = body["refresh_token"]?.jsonPrimitive?.content
                val expiresIn = body["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3600

                if (accessToken != null) {
                    Log.i(TAG, "Token exchange successful. Access token: ${accessToken.take(20)}...")
                    Log.i(TAG, "Refresh token available: ${refreshToken != null}")
                    TokenResult(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn
                    )
                } else {
                    Log.e(TAG, "Token exchange failed: no access_token in response")
                    null
                }
            } else {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Token exchange HTTP error: ${response.status} — $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange exception", e)
            null
        }
    }

    /**
     * Save tokens to SecureStorage and GoogleDriveClient.
     */
    fun saveTokens(
        context: Context,
        googleDriveClient: GoogleDriveClient,
        tokenResult: TokenResult
    ) {
        googleDriveClient.saveTokens(
            accessToken = tokenResult.accessToken,
            refreshToken = tokenResult.refreshToken
        )
        Log.i(TAG, "Tokens saved for Drive access")
    }

    /**
     * Sign out and clear tokens.
     */
    fun signOut(context: Context, googleDriveClient: GoogleDriveClient) {
        val signInClient = getSignInClient(context)
        signInClient.signOut().addOnCompleteListener {
            googleDriveClient.clearTokens()
            Log.i(TAG, "Signed out from Google Drive")
        }
    }

    private fun getWebClientId(context: Context): String {
        return com.elysium.vanguard.recordshield.BuildConfig.OAUTH_CLIENT_ID.ifEmpty { "" }
    }

    private fun getWebClientSecret(context: Context): String {
        return com.elysium.vanguard.recordshield.BuildConfig.OAUTH_CLIENT_SECRET
    }
}

data class TokenResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int
)
