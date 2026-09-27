package com.example.util

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/**
 * Centralized App Error representations across all network and data layers.
 */
sealed class AppError(
    open val userFriendlyMessage: String,
    open val originalThrowable: Throwable? = null
) {
    /**
     * Triggered when device has no internet or host is unreachable.
     */
    data class NoInternetConnection(
        override val userFriendlyMessage: String = "İnternet bağlantısı kesildi. Lütfen ağ ayarlarınızı kontrol edin.",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)

    /**
     * Triggered when socket or connection times out.
     */
    data class Timeout(
        override val userFriendlyMessage: String = "Sunucu yanıt vermedi. İstek zaman aşımına uğradı.",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)

    /**
     * Triggered when the server returns 5xx or HTTP error codes.
     */
    data class ServerError(
        val code: Int,
        val serverMessage: String? = null,
        override val userFriendlyMessage: String = "Sunucu hatası oluştu (Kod: $code).",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)

    /**
     * Triggered when JSON, M3U, or XML parsing fails.
     */
    data class ParsingError(
        val details: String? = null,
        override val userFriendlyMessage: String = "Veri işleme hatası. Alınan içerik çözümlenemedi.",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)

    /**
     * Triggered when HTTP 401 or 403 authorization error occurs.
     */
    data class Unauthorized(
        override val userFriendlyMessage: String = "Yetkilendirme hatası. Lütfen API anahtarınızı veya giriş bilgilerinizi kontrol edin.",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)

    /**
     * Triggered when HTTP 404 Resource Not Found occurs.
     */
    data class NotFound(
        override val userFriendlyMessage: String = "İstenen içerik veya kaynak sunucuda bulunamadı.",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)

    /**
     * Triggered for OutOfMemory or Corrupted Cache / DB errors.
     */
    data class OutOfMemoryOrCorruptedData(
        override val userFriendlyMessage: String = "Veritabanı veya bellek sınırı aşıldı. Önbellek optimize edilerek güvenle sıfırlandı.",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)

    /**
     * Triggered for unhandled or unknown system/network exceptions.
     */
    data class Unknown(
        override val userFriendlyMessage: String = "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.",
        override val originalThrowable: Throwable? = null
    ) : AppError(userFriendlyMessage, originalThrowable)
}

/**
 * Universal Sealed Result Wrapper for Network Operations
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val appError: AppError) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()

    fun getOrNull(): T? = if (this is Success) data else null
}

/**
 * Centralized ErrorHandlingManager
 * Catches, parses, logs, and broadcasts errors across all network and data layers.
 */
object ErrorHandlingManager {

    private const val TAG = "ErrorHandlingManager"

    private val _errorEvents = MutableSharedFlow<AppError>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * SharedFlow stream of global error events that UI components (Snackbar/Toast/Dialogs) can observe.
     */
    val errorEvents: SharedFlow<AppError> = _errorEvents.asSharedFlow()

    /**
     * Converts any Throwable into a structured AppError sealed class instance.
     */
    fun parseThrowable(throwable: Throwable): AppError {
        if (throwable is CancellationException) {
            throw throwable
        }

        Log.e(TAG, "Parsing throwable: ${throwable.javaClass.simpleName} - ${throwable.message}", throwable)

        return when (throwable) {
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
            is SSLException -> {
                AppError.NoInternetConnection(originalThrowable = throwable)
            }

            is SocketTimeoutException,
            is TimeoutException -> {
                AppError.Timeout(originalThrowable = throwable)
            }

            is HttpException -> {
                val code = throwable.code()
                val errorBody = try {
                    throwable.response()?.errorBody()?.string()
                } catch (e: Exception) {
                    null
                }

                when (code) {
                    401, 403 -> AppError.Unauthorized(originalThrowable = throwable)
                    404 -> AppError.NotFound(originalThrowable = throwable)
                    in 500..599 -> AppError.ServerError(
                        code = code,
                        serverMessage = errorBody,
                        userFriendlyMessage = "Sunucu geçici olarak hizmet veremiyor (Kod: $code).",
                        originalThrowable = throwable
                    )
                    else -> AppError.ServerError(
                        code = code,
                        serverMessage = errorBody,
                        originalThrowable = throwable
                    )
                }
            }

            is OutOfMemoryError -> {
                System.gc()
                AppError.OutOfMemoryOrCorruptedData(
                    userFriendlyMessage = "Cihaz belleği doldu. Önbellek optimize edilerek güvenle sıfırlandı.",
                    originalThrowable = throwable
                )
            }

            is android.database.sqlite.SQLiteException,
            is android.database.sqlite.SQLiteDiskIOException -> {
                AppError.OutOfMemoryOrCorruptedData(
                    userFriendlyMessage = "Veritabanı erişiminde hata oluştu. Veriler güvenle onarılıyor.",
                    originalThrowable = throwable
                )
            }

            is org.json.JSONException,
            is JsonDataException,
            is com.squareup.moshi.JsonEncodingException -> {
                AppError.ParsingError(
                    details = throwable.message,
                    originalThrowable = throwable
                )
            }

            is NullPointerException -> {
                AppError.ParsingError(
                    details = "Beklenen veri alanı eksik veya bozuk.",
                    userFriendlyMessage = "Veriler bozulmuş veya eksik. Lütfen listeyi yenileyin.",
                    originalThrowable = throwable
                )
            }

            is IOException -> {
                val msg = throwable.message.orEmpty()
                if (msg.contains("timeout", ignoreCase = true)) {
                    AppError.Timeout(originalThrowable = throwable)
                } else if (msg.contains("unable to resolve host", ignoreCase = true) ||
                    msg.contains("no route to host", ignoreCase = true) ||
                    msg.contains("connection refused", ignoreCase = true)
                ) {
                    AppError.NoInternetConnection(originalThrowable = throwable)
                } else if (msg.contains("m3u", ignoreCase = true) || msg.contains("parse", ignoreCase = true)) {
                    AppError.ParsingError(details = msg, originalThrowable = throwable)
                } else {
                    AppError.Unknown(
                        userFriendlyMessage = "Bağlantı hatası: ${throwable.localizedMessage.ifEmpty { "Sunucu erişilemiyor" }}",
                        originalThrowable = throwable
                    )
                }
            }

            else -> {
                AppError.Unknown(
                    userFriendlyMessage = throwable.localizedMessage?.ifEmpty { "Beklenmeyen bir hata oluştu." }
                        ?: "Beklenmeyen bir hata oluştu.",
                    originalThrowable = throwable
                )
            }
        }
    }

    /**
     * Safely executes an async network/API call block and returns a NetworkResult.
     * Automatically parses exceptions into AppError and broadcasts them on errorEvents flow.
     */
    suspend fun <T> safeApiCall(
        broadcastError: Boolean = true,
        apiCall: suspend () -> T
    ): NetworkResult<T> {
        return try {
            val result = apiCall()
            NetworkResult.Success(result)
        } catch (e: Throwable) {
            val appError = parseThrowable(e)
            if (broadcastError) {
                emitError(appError)
            }
            NetworkResult.Error(appError)
        }
    }

    /**
     * Broadcasts an AppError to subscribers across the app.
     */
    fun emitError(error: AppError) {
        Log.w(TAG, "Emitting error event: ${error.userFriendlyMessage}")
        _errorEvents.tryEmit(error)
    }

    /**
     * Convenience method to parse and broadcast a raw throwable.
     */
    fun emitThrowable(throwable: Throwable): AppError {
        val appError = parseThrowable(throwable)
        emitError(appError)
        return appError
    }

    /**
     * Returns a human-friendly error string for UI presentation.
     */
    fun getDisplayMessage(error: AppError): String {
        return error.userFriendlyMessage
    }
}
