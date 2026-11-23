package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AcousticSample(
    val currentDb: Int,
    val bars: List<Float>,
    val isActive: Boolean = true,
    val errorMessage: String? = null
)

class DecibelMonitorEngine {

    @SuppressLint("MissingPermission")
    fun observeDecibels(): Flow<AcousticSample> = flow {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)

        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            emit(
                AcousticSample(
                    currentDb = 0,
                    bars = List(24) { 0.08f },
                    isActive = false,
                    errorMessage = "Audio hardware buffer unavailable on this device."
                )
            )
            return@flow
        }

        val bufferSize = maxOf(minBuf, 2048)
        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                emit(
                    AcousticSample(
                        currentDb = 0,
                        bars = List(24) { 0.08f },
                        isActive = false,
                        errorMessage = "Microphone initialization failed. Check permission or device audio input."
                    )
                )
                return@flow
            }

            recorder.startRecording()
            val buffer = ShortArray(1024)
            var smoothedDb = 38.0

            while (currentCoroutineContext().isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = buffer[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read).coerceAtLeast(1.0)
                    // Standard 16-bit PCM to approximate SPL dB calibration
                    val rawDb = (20.0 * log10(rms / 32767.0) + 96.0).coerceIn(22.0, 115.0)
                    smoothedDb = smoothedDb * 0.55 + rawDb * 0.45

                    val barCount = 24
                    val chunk = (read / barCount).coerceAtLeast(1)
                    val bars = List(barCount) { idx ->
                        val start = idx * chunk
                        val end = minOf(start + chunk, read)
                        var peak = 0
                        for (j in start until end) {
                            val v = abs(buffer[j].toInt())
                            if (v > peak) peak = v
                        }
                        ((peak / 20000f) + 0.08f).coerceIn(0.08f, 1.0f)
                    }

                    emit(
                        AcousticSample(
                            currentDb = smoothedDb.toInt(),
                            bars = bars,
                            isActive = true
                        )
                    )
                }
            }
        } catch (se: SecurityException) {
            emit(
                AcousticSample(
                    currentDb = 0,
                    bars = List(24) { 0.08f },
                    isActive = false,
                    errorMessage = se.localizedMessage ?: "Microphone permission is required."
                )
            )
        } catch (e: Exception) {
            emit(
                AcousticSample(
                    currentDb = 0,
                    bars = List(24) { 0.08f },
                    isActive = false,
                    errorMessage = e.localizedMessage ?: "Audio measurement error."
                )
            )
        } finally {
            try {
                if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)
}
