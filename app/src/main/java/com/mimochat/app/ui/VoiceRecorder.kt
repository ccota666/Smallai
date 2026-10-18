package com.mimochat.app.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** 简单 M4A 录音器：start() 开始，stop() 返回文件 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        stop()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val f = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        outFile = f
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16000)
        r.setAudioEncodingBitRate(64000)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        return f
    }

    fun stop(): File? {
        val r = recorder ?: return outFile
        try {
            r.stop()
        } catch (_: Exception) {
        }
        try {
            r.release()
        } catch (_: Exception) {
        }
        recorder = null
        return outFile
    }

    fun cancel() {
        val r = recorder ?: return
        try {
            r.stop()
        } catch (_: Exception) {
        }
        try {
            r.release()
        } catch (_: Exception) {
        }
        recorder = null
        outFile?.delete()
        outFile = null
    }
}
