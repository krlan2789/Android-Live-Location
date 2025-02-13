package com.singularity_code.live_location.util

import android.content.Context
import android.util.Log
import arrow.core.right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.*

var _isConnected: Boolean = false
var WebSocket.isConnected: Boolean
    get() = _isConnected
    set(value) {
        _isConnected = value
    }

var _alwaysFail: Int = 0
var WebSocket.alwaysFail: Boolean
    get() = _alwaysFail > 32
    set(value) {}

fun websocket(
    context: Context,
    apiURL: String,
    headers: HashMap<String, String>
): Flow<WebSocket>? {
    if (apiURL.isEmpty()) return null

    val error = MutableStateFlow("")
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    val client = OkHttpClient.Builder()
        .addInterceptor(chuckerInterceptor(context))
        .build()
    val request = Request.Builder()
        .apply {
            url(apiURL)
            headers.forEach {
                addHeader(
                    it.key, it.value
                )
            }
        }
        .build()

    val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d("WSS_TAG", "WebSocket: Opened ${response.message}")
            webSocket.isConnected = true
            _alwaysFail= 0
            super.onOpen(webSocket, response)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket.alwaysFail) return
            super.onFailure(webSocket, t, response)
            _alwaysFail++
            Log.w("WSS_TAG", "WebSocket: Failed ${t.message} | ${t.stackTraceToString()} | ${response?.message} | ${response?.body}")
            webSocket.isConnected = false

            // IMPORTANT trigger recreate socket
            val errorLog = hashMapOf(
                "Error Time" to System.currentTimeMillis(),
                "Message" to t.stackTraceToString()
            )

            if (webSocket.alwaysFail) webSocket.close(1000, "To much failure closure")

            coroutineScope.launch {
                error.emit(errorLog.toString())
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("WSS_TAG", "WebSocket: Closed")
            webSocket.isConnected = false
            super.onClosed(webSocket, code, reason)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket.alwaysFail) return
            Log.d("WSS_TAG", "WebSocket: Message: $text")
            super.onMessage(webSocket, text)
        }
    }

    return error.map {
        client.newWebSocket(
            request,
            listener
        )
    }
}
