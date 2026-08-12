package com.kosmos.app.platform.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioRecorder]
 * 핵심 역할: 사용자의 마이크 입력을 받아 Gemma 모델이 지원하는 16kHz Mono 16-bit PCM 포맷의 WAV 파일로 녹음합니다.
 * Architecture Context: Platform Layer (Device Capability). Android AudioRecord API를 감싸며 앱 내부 캐시 디렉터리에 파일을 생성합니다.
 * Key Flow:
 * 1. startRecording() 호출 시 백그라운드 코루틴을 통해 Raw PCM 데이터를 읽고 파일에 씁니다.
 * 2. stopRecording() 호출 시 코루틴을 종료하고 WAV 헤더를 갱신한 뒤 파일 경로를 반환합니다.
 */
@Singleton
open class AudioRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var recordingJob: Job? = null
    // [WHY] SupervisorJob이 없으면 한 녹음 잡의 실패가 스코프를 취소시켜 이후 모든 녹음이 무음 실패한다.
    private val coroutineScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    // [WHY] 메인 스레드에서 쓰고 IO 스레드에서 읽는 루프 종료 플래그이므로 가시성 보장이 필요하다.
    @Volatile
    private var isRecording = false

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    open fun startRecording(): com.kosmos.app.core.common.AppResult<Unit> {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return com.kosmos.app.core.common.AppResult.Failure(com.kosmos.app.core.common.AppError.SttError("Record audio permission not granted"))
        }

        return try {
            outputFile = File(context.cacheDir, "kosmos_audio_input.wav")
            if (outputFile?.exists() == true) {
                outputFile?.delete()
            }

            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                return com.kosmos.app.core.common.AppResult.Failure(com.kosmos.app.core.common.AppError.SttError("Invalid buffer size: $bufferSize"))
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNELS,
                ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return com.kosmos.app.core.common.AppResult.Failure(com.kosmos.app.core.common.AppError.SttError("AudioRecord initialization failed"))
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = coroutineScope.launch {
                writeAudioDataToFile(bufferSize)
            }

            com.kosmos.app.core.common.AppResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            com.kosmos.app.core.common.AppResult.Failure(com.kosmos.app.core.common.AppError.SttError(e.message ?: "Failed to start recording"))
        }
    }

    open suspend fun stopRecording(): com.kosmos.app.core.common.AppResult<File> {
        return try {
            // [WHY] release()를 writer 코루틴 종료 전에 호출하면 해제된 recorder에 read()가
            // 들어가 undefined behavior가 된다. 플래그 해제 → writer join → stop/release 순서를 지킨다.
            isRecording = false
            recordingJob?.cancelAndJoin()
            recordingJob = null

            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null

            val file = outputFile
            if (file != null && file.exists()) {
                updateWavHeader(file)
                com.kosmos.app.core.common.AppResult.Success(file)
            } else {
                com.kosmos.app.core.common.AppResult.Failure(com.kosmos.app.core.common.AppError.SttError("Output file is null or does not exist"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            recordingJob?.cancelAndJoin()
            recordingJob = null
            runCatching { audioRecord?.release() }
            audioRecord = null
            com.kosmos.app.core.common.AppResult.Failure(com.kosmos.app.core.common.AppError.SttError(e.message ?: "Failed to stop recording"))
        }
    }

    private fun writeAudioDataToFile(bufferSize: Int) {
        val data = ByteArray(bufferSize)
        val file = outputFile ?: return
        var os: FileOutputStream? = null

        try {
            os = FileOutputStream(file)
            // 쓰기 시작 시 더미 헤더 작성
            val header = getWavHeader(0, 0, SAMPLE_RATE, 1, 16)
            os.write(header)

            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                if (read > 0) {
                    os.write(data, 0, read)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data", e)
        } finally {
            try {
                os?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing OutputStream", e)
            }
        }
    }

    private fun updateWavHeader(file: File) {
        try {
            val totalAudioLen = file.length() - 44
            val totalDataLen = totalAudioLen + 36
            val header = getWavHeader(totalAudioLen, totalDataLen, SAMPLE_RATE, 1, 16)

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to update WAV header", e)
        }
    }

    private fun getWavHeader(
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val header = ByteArray(44)
        val byteRate = sampleRate * channels * bitsPerSample / 8

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        
        return header
    }
}
