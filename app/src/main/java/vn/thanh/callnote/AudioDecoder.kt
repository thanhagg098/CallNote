package vn.thanh.callnote

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Giải mã file ghi âm (m4a/amr/...) thành mẫu float mono 16 kHz cho whisper. */
object AudioDecoder {
    private const val TARGET_RATE = 16000
    private const val MAX_SECONDS = 60 * 60 // giới hạn 60 phút / cuộc

    fun decode(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                track = i; format = f; break
            }
        }
        require(track >= 0 && format != null) { "Không tìm thấy luồng âm thanh" }
        extractor.selectTrack(track)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false
        val maxSamples = MAX_SECONDS.toLong() * 48000
        val buf = FloatBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val bb = outBuf.slice().order(ByteOrder.nativeOrder())
                        val ch = channels.coerceAtLeast(1)
                        if (isFloat) {
                            val fb = bb.asFloatBuffer()
                            val frames = fb.remaining() / ch
                            for (f in 0 until frames) {
                                var s = 0f
                                for (c in 0 until ch) s += fb.get(f * ch + c)
                                buf.add(s / ch)
                            }
                        } else {
                            val sb = bb.asShortBuffer()
                            val frames = sb.remaining() / ch
                            for (f in 0 until frames) {
                                var s = 0f
                                for (c in 0 until ch) s += sb.get(f * ch + c) / 32768f
                                buf.add(s / ch)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (buf.size > maxSamples) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop(); codec.release(); extractor.release()
        }
        return normalize(resample(buf.toArray(), sampleRate, TARGET_RATE))
    }

    /** Bỏ lệch DC và đưa âm lượng về mức chuẩn (ghi âm cuộc gọi thường nhỏ, không đều). */
    private fun normalize(x: FloatArray): FloatArray {
        if (x.isEmpty()) return x
        var mean = 0.0
        for (v in x) mean += v
        mean /= x.size
        var sq = 0.0
        var peak = 0f
        for (i in x.indices) {
            val v = (x[i] - mean).toFloat(); x[i] = v
            sq += v * v; if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        }
        val rms = kotlin.math.sqrt(sq / x.size).toFloat()
        if (rms < 1e-5f) return x
        val gain = minOf(0.1f / rms, 0.95f / peak, 20f)
        for (i in x.indices) x[i] *= gain
        return x
    }

    /** Đổi tần số mẫu bằng bộ lọc sinc có cửa sổ (chống méo/rè khi hạ tần số). */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val ratio = from.toDouble() / to
        val outLen = (input.size / ratio).toInt()
        val out = FloatArray(outLen)
        val cutoff = if (to < from) 0.92 * to / from else 0.92  // tỉ lệ so với Nyquist đầu vào
        val half = 16
        val scale = if (to < from) ratio else 1.0
        val width = (half * scale).toInt().coerceAtLeast(half)
        for (i in 0 until outLen) {
            val center = i * ratio
            val c0 = center.toInt()
            var acc = 0.0
            var norm = 0.0
            for (k in c0 - width + 1..c0 + width) {
                if (k < 0 || k >= input.size) continue
                val t = k - center
                val xw = t / width
                if (xw <= -1.0 || xw >= 1.0) continue
                val arg = Math.PI * cutoff * t
                val sinc = if (kotlin.math.abs(arg) < 1e-9) 1.0 else kotlin.math.sin(arg) / arg
                val win = 0.5 * (1 + kotlin.math.cos(Math.PI * xw))   // cửa sổ Hann
                val w = sinc * win
                acc += input[k] * w
                norm += w
            }
            out[i] = if (norm != 0.0) (acc / norm).toFloat() else 0f
        }
        return out
    }

    private class FloatBuilder {
        private var data = FloatArray(1 shl 20)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
