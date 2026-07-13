package com.example.skillroundtable.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.skillroundtable.data.RoundtableDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class AudioTranscodeWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AudioTranscodeWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val messageId = inputData.getLong("message_id", -1L)
        val wavPath = inputData.getString("wav_path") ?: return@withContext Result.failure()
        
        if (messageId == -1L) return@withContext Result.failure()
        
        val wavFile = File(wavPath)
        if (!wavFile.exists()) {
            Log.e(TAG, "待转码 WAV 文件不存在: $wavPath")
            return@withContext Result.failure()
        }

        val aacPath = wavPath.replace(".wav", ".aac")
        val aacFile = File(aacPath)

        try {
            Log.d(TAG, "开始后台转码: $wavPath -> $aacPath")
            encodePcmToAac(wavFile, aacFile)
            
            val database = RoundtableDatabase.getDatabase(applicationContext, this)
            val size = aacFile.length()
            database.chatDao().updateMessageAudio(messageId, aacPath, "aac", size)

            if (wavFile.exists()) {
                wavFile.delete()
            }
            Log.d(TAG, "转码成功完成并已删除原 WAV")
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "音频转码发生异常", e)
            if (aacFile.exists()) {
                aacFile.delete()
            }
            return@withContext Result.failure()
        }
    }

    @Suppress("DEPRECATION")
    private fun encodePcmToAac(wavFile: File, aacFile: File) {
        val sampleRate = 24000
        val channels = 1
        val bitRate = 64000

        val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 10)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val fis = FileInputStream(wavFile)
        fis.skip(44)

        val fos = FileOutputStream(aacFile)

        val inputBuffers = encoder.inputBuffers
        val outputBuffers = encoder.outputBuffers
        val bufferInfo = MediaCodec.BufferInfo()

        val tempBuffer = ByteArray(4096)
        var hasMoreData = true
        var presentationTimeUs = 0L

        while (hasMoreData || bufferInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
            if (hasMoreData) {
                val inputIndex = encoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = inputBuffers[inputIndex]
                    inputBuffer.clear()
                    
                    val bytesRead = fis.read(tempBuffer)
                    if (bytesRead == -1) {
                        encoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        hasMoreData = false
                    } else {
                        inputBuffer.put(tempBuffer, 0, bytesRead)
                        encoder.queueInputBuffer(inputIndex, 0, bytesRead, presentationTimeUs, 0)
                        
                        val numSamples = bytesRead / 2
                        presentationTimeUs += (numSamples * 1_000_000L) / sampleRate
                    }
                }
            }

            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = outputBuffers[outputIndex]
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                val outData = ByteArray(bufferInfo.size)
                outputBuffer.get(outData)

                val adtsHeader = ByteArray(7)
                addADTStoPacket(adtsHeader, bufferInfo.size + 7)
                fos.write(adtsHeader)
                fos.write(outData)

                encoder.releaseOutputBuffer(outputIndex, false)
            }
        }

        encoder.stop()
        encoder.release()
        fis.close()
        fos.close()
    }

    private fun addADTStoPacket(packet: ByteArray, packetLen: Int) {
        val profile = 2  
        val freqIdx = 6  
        val chanCfg = 1  

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }
}
