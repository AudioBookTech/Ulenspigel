package com.KonstantinShramko.Audiobook

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LyraDecoder private constructor(
    val sampleRate: Int = 16000,
    val numChannels: Int = 1,
    val bitrate: Int = 3200
) {
    private var nativeHandle: Long = 0L

    /**
     * Direct ByteBuffer for zero-copy PCM data transfer from JNI.
     * 16000 Hz / 50 fps = 320 samples per frame. 
     * 320 * 2 bytes (short) = 640 bytes. 
     * We allocate 1024 bytes to be safe for potential future changes.
     */
    val pcmBuffer: ByteBuffer = ByteBuffer.allocateDirect(1024)
        .order(ByteOrder.nativeOrder())

    companion object {
        private const val TAG = "LyraDecoder"

        init {
            // Load the monolithic native library.
            System.loadLibrary("lyra_decoder")
        }

        fun create(sampleRate: Int = 16000, numChannels: Int = 1, bitrate: Int = 3200): LyraDecoder? {
            Log.d(TAG, "Creating LyraDecoder: rate=$sampleRate, channels=$numChannels, bitrate=$bitrate")
            val decoder = LyraDecoder(sampleRate, numChannels, bitrate)
            if (decoder.initNative()) {
                return decoder
            }
            return null
        }

        fun parseBitrateFromFileName(fileName: String): Int {
            return when {
                fileName.contains("Q1", ignoreCase = true) -> 3200
                fileName.contains("Q2", ignoreCase = true) -> 6000
                fileName.contains("Q3", ignoreCase = true) -> 9200
                else -> 3200
            }
        }

        fun getFrameSizeBytes(bitrate: Int): Int {
            return when (bitrate) {
                3200 -> 8  // Q1
                6000 -> 15 // Q2
                9200 -> 23 // Q3
                else -> 8
            }
        }
    }

    // Updated JNI signatures to match jni_lyra_decoder_optimized.cc
    private external fun init(sampleRateHz: Int, numChannels: Int, bitrate: Int): Long
    private external fun decodeToBuffer(decoderPtr: Long, encodedData: ByteArray, outBuffer: ByteBuffer): Int
    private external fun release(decoderPtr: Long)

    private fun initNative(): Boolean {
        nativeHandle = init(sampleRate, numChannels, bitrate)
        if (nativeHandle == 0L) {
            Log.e(TAG, "C++ init() returned 0")
            return false
        }
        Log.d(TAG, "C++ LyraDecoder created! Handle = $nativeHandle")
        return true
    }

    /**
     * Decodes an encoded frame into the internal [pcmBuffer].
     * @return Number of samples (shorts) written to the buffer, or a negative error code.
     */
    fun decodeFrameToBuffer(encodedFrame: ByteArray): Int {
        if (nativeHandle == 0L) return -1
        pcmBuffer.clear()
        return decodeToBuffer(nativeHandle, encodedFrame, pcmBuffer)
    }

    fun reset() {
        if (nativeHandle != 0L) {
            release(nativeHandle)
            nativeHandle = 0L
        }
        initNative()
    }

    fun releaseDecoder() {
        if (nativeHandle != 0L) {
            release(nativeHandle)
            nativeHandle = 0L
            Log.d(TAG, "C++ LyraDecoder memory released")
        }
    }
}
