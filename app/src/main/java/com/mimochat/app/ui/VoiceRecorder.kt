package com.mimochat.app.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * 录音为 WAV（ASR 只收 mp3/wav）。
 * start() 开始；stop() 返回 wav 文件。
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: AudioRecord? = null
    private var recThread: Thread? = null
    private var outFile: File? = null
    @Volatile private var running = false

    val isRecording: Boolean get() = running

    fun start(): File {
        stop()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val f = File(dir, "rec_${System.currentTimeMillis()}.wav")
        outFile = f

        val sampleRate = 16000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate, channel, encoding,
            maxOf(minBuf, 8192)
        )
        recorder = rec
        running = true

        // 先写 44 字节 WAV 头占位，录完回填长度
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(0)
            writeWavHeader(raf, 0)
        }

        rec.startRecording()
        recThread = thread(name = "wav-rec") {
            val buf = ByteArray(4096)
            try {
                RandomAccessFile(f, "rw").use { raf ->
                    raf.seek(44)
                    while (running) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n > 0) raf.write(buf, 0, n)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return f
    }

    fun stop(): File? {
        running = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recThread?.join(800)
        recThread = null
        // 回填 WAV 长度
        val f = outFile ?: return null
        try {
            RandomAccessFile(f, "rw").use { raf ->
                val dataLen = raf.length() - 44
                writeWavHeader(raf, dataLen.toInt())
            }
        } catch (_: Exception) {
        }
        return f
    }

    fun cancel() {
        running = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recThread = null
        outFile?.delete()
        outFile = null
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataLen: Int) {
        val sampleRate = 16000
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.write(intLE(36 + dataLen))
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.write(intLE(16))
        raf.write(shortLE(1))            // PCM
        raf.write(shortLE(channels))
        raf.write(intLE(sampleRate))
        raf.write(intLE(byteRate))
        raf.write(shortLE(channels * bits / 8))
        raf.write(shortLE(bits))
        raf.writeBytes("data")
        raf.write(intLE(dataLen))
    }

    private fun intLE(v: Int): ByteArray = byteArrayOf(
        (v and 0xff).toByte(), (v shr 8 and 0xff).toByte(),
        (v shr 16 and 0xff).toByte(), (v shr 24 and 0xff).toByte()
    )

    private fun shortLE(v: Int): ByteArray =
        byteArrayOf((v and 0xff).toByte(), (v shr 8 and 0xff).toByte())
}
