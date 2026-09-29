package com.carcast.mirror.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build

/** Creates an allowed device-playback PCM AudioRecord; never uses the microphone. */
class PlaybackCaptureAudioSource(
    private val projection: MediaProjection,
    private val onStatus: (String) -> Unit
) {
    private val sampleRate = 48_000
    private val channels = 2

    fun createAudioRecord(): AudioRecord {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) error("AudioPlaybackCapture requires Android 10 or newer")
        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(sampleRate / 10 * channels * 2)
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val record = AudioRecord.Builder()
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelMask).build())
            .setBufferSizeInBytes(minBuffer)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioPlaybackCapture AudioRecord was not initialized")
        }
        onStatus("Audio: Capturing device playback")
        return record
    }
}
