package com.echoshield.calling.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.File
import java.util.UUID

/**
 * WebRTC Calling and Audio Engine for EchoShield.
 * Handles peer connection setup, ICE candidate negotiation, local microphone audio,
 * speaker routing, and tapping the remote caller's audio stream for Phase 7 analysis.
 */
class WebRtcCallManager(
    private val context: Context
) {
    private val tag = "EchoWebRtcManager"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioDeviceModule: AudioDeviceModule? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    val remoteAudioCaptureSink = RemoteAudioCaptureSink()

    var onIceCandidateGenerated: ((sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit)? = null
    var onConnectionStateChanged: ((PeerConnection.IceConnectionState) -> Unit)? = null

    private val queuedRemoteCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
    )

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
            .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(err: String?) {
                    Log.e(tag, "AudioRecord init error: $err")
                }
                override fun onWebRtcAudioRecordStartError(
                    code: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                    err: String?
                ) {
                    Log.e(tag, "AudioRecord start error: $code $err")
                }
                override fun onWebRtcAudioRecordError(err: String?) {
                    Log.e(tag, "AudioRecord error: $err")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(err: String?) {
                    Log.e(tag, "AudioTrack init error: $err")
                }
                override fun onWebRtcAudioTrackStartError(
                    code: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                    err: String?
                ) {
                    Log.e(tag, "AudioTrack start error: $code $err")
                }
                override fun onWebRtcAudioTrackError(err: String?) {
                    Log.e(tag, "AudioTrack error: $err")
                }
            })
            .createAudioDeviceModule()

        audioDeviceModule = adm

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()
    }

    fun startPeerConnection() {
        val factory = peerConnectionFactory ?: return

        // Configure system hardware audio for VoIP real-time communication
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isMicrophoneMute = false

        // Create local audio track with hardware AEC, AGC, and NS constraints
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("local_audio_track", localAudioSource)
        localAudioTrack?.setEnabled(true)

        // Configure RTC
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(tag, "Local ICE Candidate gathered: ${candidate.sdp}")
                onIceCandidateGenerated?.invoke(
                    candidate.sdpMid ?: "0",
                    candidate.sdpMLineIndex,
                    candidate.sdp
                )
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.i(tag, "WebRTC ICE Connection State: $newState")
                onConnectionStateChanged?.invoke(newState)
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = receiver.track()
                if (track is AudioTrack) {
                    Log.i(tag, "Remote AudioTrack received via onAddTrack. Attaching RemoteAudioCaptureSink.")
                    remoteAudioTrack = track
                    track.addSink(remoteAudioCaptureSink)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is AudioTrack) {
                    Log.i(tag, "Remote AudioTrack received via onTrack. Attaching RemoteAudioCaptureSink.")
                    remoteAudioTrack = track
                    track.addSink(remoteAudioCaptureSink)
                }
            }

            override fun onAddStream(stream: MediaStream) {
                if (stream.audioTracks.isNotEmpty()) {
                    val track = stream.audioTracks[0]
                    Log.i(tag, "Remote AudioTrack received via onAddStream. Attaching RemoteAudioCaptureSink.")
                    remoteAudioTrack = track
                    track.addSink(remoteAudioCaptureSink)
                }
            }

            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dataChannel: org.webrtc.DataChannel) {}
        })

        // Add local audio track to connection with explicit SEND_RECV transceiver
        localAudioTrack?.let { track ->
            try {
                peerConnection?.addTransceiver(
                    track,
                    RtpTransceiver.RtpTransceiverInit(
                        RtpTransceiver.RtpTransceiverDirection.SEND_RECV,
                        listOf("EchoAudioStream")
                    )
                )
                Log.i(tag, "Local audio track added via addTransceiver (SEND_RECV).")
            } catch (e: Exception) {
                Log.w(tag, "addTransceiver failed, falling back to addTrack: ${e.message}")
                peerConnection?.addTrack(track, listOf("EchoAudioStream"))
            }
        }
    }

    fun createOffer(onSdpReady: (SessionDescription) -> Unit) {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.i(tag, "Local SDP offer applied successfully.")
                        onSdpReady(desc)
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(tag, "Failed to set local SDP offer: $error")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Failed to create SDP offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, sdpConstraints)
    }

    fun createAnswer(onSdpReady: (SessionDescription) -> Unit) {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.i(tag, "Local SDP answer applied successfully.")
                        onSdpReady(desc)
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(tag, "Failed to set local SDP answer: $error")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Failed to create SDP answer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, sdpConstraints)
    }

    fun setRemoteDescription(type: String, sdp: String, onComplete: () -> Unit = {}) {
        val sdpType = if (type.equals("offer", ignoreCase = true)) {
            SessionDescription.Type.OFFER
        } else {
            SessionDescription.Type.ANSWER
        }
        val desc = SessionDescription(sdpType, sdp)

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(tag, "Remote SDP description ($type) set successfully.")
                drainQueuedCandidates()
                onComplete()
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(tag, "Failed to set remote SDP description: $error")
            }
        }, desc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        synchronized(queuedRemoteCandidates) {
            if (isRemoteDescriptionSet && peerConnection != null) {
                peerConnection?.addIceCandidate(iceCandidate)
                Log.d(tag, "Added remote ICE candidate immediately: $sdpMid")
            } else {
                Log.d(tag, "Queued remote ICE candidate ($sdpMid) pending remote description.")
                queuedRemoteCandidates.add(iceCandidate)
            }
        }
    }

    private fun drainQueuedCandidates() {
        synchronized(queuedRemoteCandidates) {
            isRemoteDescriptionSet = true
            Log.d(tag, "Draining ${queuedRemoteCandidates.size} queued remote ICE candidates.")
            for (cand in queuedRemoteCandidates) {
                peerConnection?.addIceCandidate(cand)
            }
            queuedRemoteCandidates.clear()
        }
    }

    fun setMicrophoneMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        Log.i(tag, "Microphone mute state set to: $muted")
    }

    fun setSpeakerphone(speakerOn: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn
            Log.i(tag, "Speakerphone route set to: $speakerOn")
        } catch (e: Exception) {
            Log.w(tag, "Failed setting speakerphone state: ${e.message}")
        }
    }

    /**
     * Extracts a rolling 16kHz PCM WAV snapshot of the remote caller's audio stream.
     * Saved in app-private storage: filesDir/pending_call_analysis.
     */
    fun exportRemoteAudioSample(): File? {
        val analysisDir = File(context.filesDir, "pending_call_analysis")
        analysisDir.mkdirs()

        val sampleFile = File(analysisDir, "remote_sample_${UUID.randomUUID().hexString.take(8)}.wav")
        val success = remoteAudioCaptureSink.exportToWav(sampleFile, minDurationSeconds = 1.5f)
        return if (success) sampleFile else null
    }

    fun close() {
        try {
            remoteAudioTrack?.removeSink(remoteAudioCaptureSink)
            remoteAudioCaptureSink.clear()
            peerConnection?.close()
            peerConnection?.dispose()
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            peerConnection = null
            localAudioTrack = null
            localAudioSource = null
            remoteAudioTrack = null

            synchronized(queuedRemoteCandidates) {
                queuedRemoteCandidates.clear()
                isRemoteDescriptionSet = false
            }

            audioDeviceModule?.release()
            audioDeviceModule = null

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.w(tag, "Error closing WebRTC call manager: ${e.message}")
        }
    }
}

private val UUID.hexString: String
    get() = toString().replace("-", "")

