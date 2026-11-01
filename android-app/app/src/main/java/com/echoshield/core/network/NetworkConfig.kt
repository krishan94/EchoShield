package com.echoshield.core.network

import com.echoshield.BuildConfig

/**
 * Centralized backend network configuration for EchoShield.
 * Provides build-aware base URLs for physical device LAN testing and emulator development.
 */
object NetworkConfig {

    /**
     * Active backend base URL resolved from Gradle build configuration.
     * In debug builds: defaults to physical device LAN URL (http://172.20.10.3:8000).
     */
    val BASE_URL: String = BuildConfig.BACKEND_BASE_URL

    /**
     * Local Wi-Fi / Hotspot LAN URL for real physical device testing.
     */
    const val LAN_BASE_URL: String = BuildConfig.LAN_BASE_URL

    /**
     * Android Emulator loopback URL (maps to 127.0.0.1 on development host machine).
     */
    const val EMULATOR_BASE_URL: String = BuildConfig.EMULATOR_BASE_URL

    /**
     * Returns the WebSocket signalling URL for real-time WebRTC calling.
     */
    fun getSignalingWebSocketUrl(token: String): String {
        val wsBase = BASE_URL.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
        return "$wsBase/api/v1/calling/ws?token=$token"
    }
}

