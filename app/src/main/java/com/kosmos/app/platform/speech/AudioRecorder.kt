package com.kosmos.app.platform.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioRecorder]
 * 핵심 역할: 사용자의 마이크 입력을 받아 임시 파일(.wav 또는 .m4a 형식 등)로 녹음합니다.
 * Architecture Context: Platform Layer (Device Capability). Android MediaRecorder API를 감싸며 앱 내부 캐시 디렉터리에 파일을 생성합니다.
 * Key Flow:
 * 1. startRecording() 호출 시 기존 임시 파일 삭제 후 녹음 시작.
 * 2. stopRecording() 호출 시 녹음 중지 및 파일 경로 반환.
 */
@Singleton
open class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    open fun startRecording(): Result<Unit> {
        return try {
            outputFile = File(context.cacheDir, "kosmos_audio_input.m4a")
            if (outputFile?.exists() == true) {
                outputFile?.delete()
            }

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                // Gemma 모델 입력용 포맷 설정
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            Result.failure(e)
        }
    }

    open fun stopRecording(): Result<File> {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            val file = outputFile
            if (file != null && file.exists()) {
                Result.success(file)
            } else {
                Result.failure(IllegalStateException("Output file is null or does not exist"))
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            Result.failure(e)
        }
    }
}
