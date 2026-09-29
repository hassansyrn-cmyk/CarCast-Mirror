package com.carcast.mirror.service

import android.content.Context
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Narrow adapter from Android playback PCM into the pinned WebRTC ADM.
 * The projection is deliberately attached only after ScreenCapturerAndroid
 * has consumed consent and successfully started video capture.
 */
class PlaybackCaptureAudioDeviceModule(
    context: Context,
    private val onStatus: (String) -> Unit
) {
    private val delegate: JavaAudioDeviceModule = JavaAudioDeviceModule.builder(context)
        .setSampleRate(48_000)
        .setInputSampleRate(48_000)
        .setUseStereoInput(true)
        .setUseLowLatency(true)
        .setEnableVolumeLogger(false)
        .createAudioDeviceModule()
    private var playbackRecord: AudioRecord? = null
    private var installed = false

    val audioDeviceModule: AudioDeviceModule get() = delegate

    /** Initializes WebRTC's audio input, but does not touch MediaProjection. */
    fun install(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onStatus("Audio: Unavailable — Android 10 or newer required")
            return false
        }
        return try {
            val input = delegate.javaClass.getDeclaredField("audioInput").apply { isAccessible = true }.get(delegate)
            val init = input.javaClass.getDeclaredMethod("initRecordingIfNeeded").apply { isAccessible = true }
            if (!(init.invoke(input) as? Boolean ?: false)) throw IllegalStateException("WebRTC audio input initialization failed")
            installed = true
            onStatus("Audio: Initializing")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "WebRTC audio input initialization failed", t)
            onStatus("Audio: Unavailable — WebRTC audio input unavailable")
            false
        }
    }

    /**
     * Uses the exact MediaProjection owned by ScreenCapturerAndroid. This is
     * the only place where AudioPlaybackCaptureConfiguration is constructed.
     */
    fun attachProjection(sharedProjection: MediaProjection): Boolean {
        if (!installed) return false
        return try {
            val input = delegate.javaClass.getDeclaredField("audioInput").apply { isAccessible = true }.get(delegate)
            val field = input.javaClass.getDeclaredField("audioRecord").apply { isAccessible = true }
            val source = PlaybackCaptureAudioSource(sharedProjection, onStatus)
            val record = source.createAudioRecord()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("Playback AudioRecord was not initialized")
            }
            field.set(input, record)
            playbackRecord = record
            onStatus("Audio: Capturing device playback")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Playback capture attachment failed", t)
            onStatus(if (t is SecurityException) "Audio: Unavailable — device audio permission denied" else "Audio: Unavailable — ${t.message ?: "projection not ready"}")
            false
        }
    }

    fun release() {
        runCatching { playbackRecord?.stop() }
        playbackRecord?.release()
        playbackRecord = null
        installed = false
        runCatching { delegate.release() }
    }

    companion object { private const val TAG = "CarCastAudioBridge" }
}
