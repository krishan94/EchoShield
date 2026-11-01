package com.echoshield.calling.signaling

import android.util.Log
import com.echoshield.core.network.NetworkConfig
import com.echoshield.domain.model.SignalingMessage
import com.echoshield.domain.model.SignalingMessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real-time WebRTC Signalling Client over secure WebSocket.
 * Communicates with EchoShield backend /api/v1/calling/ws.
 */
class SignalingClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for persistent socket
        .build()
) {
    private val tag = "EchoSignalingClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private val _incomingEvents = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incomingEvents: SharedFlow<SignalingMessage> = _incomingEvents.asSharedFlow()

    @Volatile
    var isConnected: Boolean = false
        private set

    fun connect(firebaseToken: String) {
        if (isConnected) return

        val url = NetworkConfig.getSignalingWebSocketUrl(firebaseToken)
        Log.i(tag, "Connecting to signalling server...")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.i(tag, "WebRTC Signalling WebSocket connected.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val typeStr = json.optString("type", "")
                    val type = SignalingMessageType.fromWireValue(typeStr)

                    val payloadMap = mutableMapOf<String, Any?>()
                    val payloadObj = json.optJSONObject("payload")
                    if (payloadObj != null) {
                        val keys = payloadObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            payloadMap[key] = payloadObj.get(key)
                        }
                    }

                    val message = SignalingMessage(
                        type = type,
                        callId = if (json.isNull("call_id")) null else json.optString("call_id"),
                        senderId = if (json.isNull("sender_id")) null else json.optString("sender_id"),
                        senderName = if (json.isNull("sender_name")) null else json.optString("sender_name"),
                        receiverId = if (json.isNull("receiver_id")) null else json.optString("receiver_id"),
                        payload = payloadMap,
                        timestamp = if (json.isNull("timestamp")) null else json.optString("timestamp")
                    )

                    scope.launch {
                        _incomingEvents.emit(message)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse incoming signalling packet: ${e.message}", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.i(tag, "Signalling socket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.i(tag, "Signalling socket closed: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.w(tag, "Signalling socket transport failure: ${t.message}")
            }
        })
    }

    private fun sendJson(json: JSONObject): Boolean {
        val ws = webSocket
        return if (ws != null && isConnected) {
            ws.send(json.toString())
        } else {
            Log.w(tag, "Cannot send packet: WebSocket is not connected.")
            false
        }
    }

    fun sendCallInitiate(callId: String, receiverId: String, receiverName: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.CALL_INITIATE.wireValue)
            put("call_id", callId)
            put("receiver_id", receiverId)
            put("receiver_name", receiverName)
        }
        return sendJson(json)
    }

    fun sendCallRinging(callId: String, callerId: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.CALL_RINGING.wireValue)
            put("call_id", callId)
            put("receiver_id", callerId)
        }
        return sendJson(json)
    }

    fun sendCallAccepted(callId: String, callerId: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.CALL_ACCEPTED.wireValue)
            put("call_id", callId)
            put("receiver_id", callerId)
        }
        return sendJson(json)
    }

    fun sendCallRejected(callId: String, callerId: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.CALL_REJECTED.wireValue)
            put("call_id", callId)
            put("receiver_id", callerId)
        }
        return sendJson(json)
    }

    fun sendCallEnded(callId: String, peerId: String, durationSeconds: Int, reason: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.CALL_ENDED.wireValue)
            put("call_id", callId)
            put("receiver_id", peerId)
            put("payload", JSONObject().apply {
                put("duration_seconds", durationSeconds)
                put("reason", reason)
            })
        }
        return sendJson(json)
    }

    fun sendSdpOffer(callId: String, receiverId: String, sdp: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.OFFER.wireValue)
            put("call_id", callId)
            put("receiver_id", receiverId)
            put("payload", JSONObject().apply {
                put("sdp", sdp)
                put("type", "offer")
            })
        }
        return sendJson(json)
    }

    fun sendSdpAnswer(callId: String, receiverId: String, sdp: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.ANSWER.wireValue)
            put("call_id", callId)
            put("receiver_id", receiverId)
            put("payload", JSONObject().apply {
                put("sdp", sdp)
                put("type", "answer")
            })
        }
        return sendJson(json)
    }

    fun sendIceCandidate(callId: String, receiverId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String): Boolean {
        val json = JSONObject().apply {
            put("type", SignalingMessageType.ICE_CANDIDATE.wireValue)
            put("call_id", callId)
            put("receiver_id", receiverId)
            put("payload", JSONObject().apply {
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
                put("candidate", candidate)
            })
        }
        return sendJson(json)
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {}
        webSocket = null
        isConnected = false
    }

    /**
     * Allows test suites to dispatch mock signalling messages.
     */
    fun emitEventForTest(message: SignalingMessage) {
        _incomingEvents.tryEmit(message)
    }
}

