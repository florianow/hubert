package com.hubert.utils

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Records audio from the microphone as PCM 16-bit mono at 16 kHz,
 * and produces a WAV byte array ready for Azure Pronunciation Assessment.
 *
 * Not a singleton — create per recording session.
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var recorder: AudioRecord? = null
    private var isRecording = false

    private val pcmBuffer = ByteArrayOutputStream()

    /**
     * Start recording. Call from a coroutine context.
     * This suspends while recording — call [stop] from another coroutine to end it.
     * @throws IllegalStateException if AudioRecord could not be initialized (e.g. missing permission)
     */
    @SuppressLint("MissingPermission")
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            .coerceAtLeast(4096)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException(
                "AudioRecord failed to initialize. Check that RECORD_AUDIO permission is granted."
            )
        }

        recorder = rec
        pcmBuffer.reset()
        isRecording = true
        rec.startRecording()

        val buffer = ByteArray(bufferSize)
        while (isRecording) {
            val read = recorder?.read(buffer, 0, buffer.size) ?: -1
            if (read > 0) {
                pcmBuffer.write(buffer, 0, read)
            }
        }
    }

    /**
     * Stop recording and return the audio as a WAV byte array.
     */
    fun stop(): ByteArray {
        isRecording = false
        recorder?.stop()
        recorder?.release()
        recorder = null

        val pcmData = pcmBuffer.toByteArray()
        return wrapPcmInWav(pcmData)
    }

    /**
     * Release resources without returning data (e.g., on cancel).
     */
    fun release() {
        isRecording = false
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        pcmBuffer.reset()
    }

    /**
     * Wraps raw PCM data in a standard WAV header.
     * Format: 16-bit, mono, 16 kHz — exactly what Azure Speech expects.
     */
    private fun wrapPcmInWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2  // 16-bit = 2 bytes

        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // File size - 8
        writeInt(header, 4, totalDataLen)

        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        writeInt(header, 16, 16)                   // Subchunk1Size (PCM = 16)
        writeShort(header, 20, 1)                   // AudioFormat (PCM = 1)
        writeShort(header, 22, channels)            // NumChannels
        writeInt(header, 24, SAMPLE_RATE)           // SampleRate
        writeInt(header, 28, byteRate)              // ByteRate
        writeShort(header, 32, channels * 2)        // BlockAlign
        writeShort(header, 34, 16)                  // BitsPerSample

        // data subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        writeInt(header, 40, pcmData.size)          // Subchunk2Size

        return header + pcmData
    }

    private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
