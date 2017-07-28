package com.github.m3l3m01t.snapit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Environment
import android.support.annotation.RequiresApi
import android.util.DisplayMetrics
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


/**
 * Created by 34372 on 2017/7/25.
 */
class ScreenCapturer(val context: Context, val mediaProjection: MediaProjection) {
    private var count = 0

    var mediaCodec: MediaCodec? = null

    lateinit var mediaMuxer: MediaMuxer
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    private fun createMediaFormat(mimeType: String, colorFormat: Int, width: Int, height: Int): MediaFormat {

        val frameRate = 25
        val mediaFormat = MediaFormat.createVideoFormat(mimeType, width, height)
//        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 15)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate)
        mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate)
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        return mediaFormat
    }

    private fun createEncoder(mediaFormat: MediaFormat, mimeType: String): MediaCodec? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

        var info: MediaCodecInfo? = null

        var i = 0
        while (i < codecList.codecInfos.size && info == null) {
            val curInfo = codecList.codecInfos[i]

            if (!curInfo.isEncoder) {
                i++
                continue
            }
            val types = curInfo.supportedTypes
            for (j in types.indices)
                if (types[j] == mimeType) {
                    info = curInfo
                    break
                }
            i++
        }

        if (info == null)
            return null

        val mediaCodec = MediaCodec.createByCodecName(info.name)
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            private var videoTrackIndex = 0

            @SuppressLint("WrongConstant")
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, bufInfo: MediaCodec.BufferInfo) {
                println("output available")
                if (bufInfo.flags.and(MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    return
                }

                val maxFrames: Int = 100
                if (count < maxFrames) {
                    val buf = codec.getOutputBuffer(index)
                    mediaMuxer.writeSampleData(videoTrackIndex, buf, bufInfo)
                }
                codec.releaseOutputBuffer(index, false)

                if (++count == maxFrames) {
                    mediaProjection.stop()
                    codec.signalEndOfInputStream()
                }
            }

            override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
            }

            override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
                mediaMuxer = MediaMuxer(File(dir, "/test.mp4").path,
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                videoTrackIndex = mediaMuxer.addTrack(format)
                mediaMuxer.start()
            }

            override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
                println(e?.localizedMessage)
            }

        })
//        val mediaCodec = MediaCodec.createEncoderByType(mimeType)

        try {
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: RuntimeException) {
            mediaCodec.release()

            return null
        }

        return mediaCodec
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun capture() {

        if (!dir.exists())
            dir.mkdirs()

        val displayMetrics = context.resources.displayMetrics
        val width = (displayMetrics.widthPixels + 127).and(127.inv())
        val height = displayMetrics.heightPixels

//                    val mimeType = "video/avc"

        val formats: MutableList<Int> = mutableListOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )

/*
        formats.addAll(arrayOf(0x7fa30c00,0x7fa30c03, 0x7FA30C04, 0x7f000100, 0x7F00A000,
                0x7F420888,
                0x7F422888,
                0x7F444888,
                0x7F36B888, 0x7F36A888))

        formats.addAll(1..43)
*/
        var mimeType = ""
        var mediaFormat: MediaFormat? = null

        for (t in arrayOf(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                //                MediaFormat.MIMETYPE_VIDEO_HEVC,
//                MediaFormat.MIMETYPE_VIDEO_MPEG2,
//                MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
//                MediaFormat.MIMETYPE_VIDEO_H263,
                MediaFormat.MIMETYPE_VIDEO_MPEG4
        )) {
            for (format in formats) {
                if (mediaCodec == null) {
                    mediaFormat = createMediaFormat(t, format,
                            800, 600)
//                            displayMetrics.widthPixels, (displayMetrics.heightPixels - (displayMetrics.heightPixels %
//                            16)) / 2)

                    mediaCodec = createEncoder(mediaFormat, t)
                }
                if (mediaCodec != null) {
                    mimeType = t
                    println(format)
                    break
                }
            }

            if (mediaCodec != null)
                break
        }

        if (mediaCodec == null)
            return

        println("$mimeType - $mediaFormat ")
        val surface = mediaCodec!!.createInputSurface()

        mediaCodec!!.start()
        setupProjection(displayMetrics, surface, object : VirtualDisplay
        .Callback() {
            override fun onResumed() {
                super.onResumed()
            }

            override fun onStopped() {
                super.onStopped()

                mediaCodec!!.stop()
                mediaCodec!!.release()

                mediaMuxer.stop()
                mediaMuxer.release()
            }

            override fun onPaused() {
                super.onPaused()
            }
        })
    }

    private fun setupProjection(displayMetrics: DisplayMetrics, surface: Surface, cb: VirtualDisplay.Callback) {
        val width = (displayMetrics.widthPixels + 127).and(127.inv())
        val height = displayMetrics.heightPixels

        mediaProjection.createVirtualDisplay("virtual",
                width,
                height,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface, cb, null
        )
    }

    fun capture2() {
        val displayMetrics = context.resources.displayMetrics
        val width = (displayMetrics.widthPixels + 127).and(127.inv())
        val height = displayMetrics.heightPixels

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 8)

        reader.setOnImageAvailableListener({
            val image = it.acquireLatestImage()
            if (image != null) {
                val planeBuf = image.planes[0].buffer
                val bitmapConfig = Bitmap.Config.ARGB_8888
                val bitmap = Bitmap.createBitmap(width, height, bitmapConfig)

                bitmap.copyPixelsFromBuffer(planeBuf)
                val ostream = FileOutputStream(
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                "test_${count}.bmp"))
                count++

                val bos = ByteArrayOutputStream(width * height * 4)
                bitmap.compress(Bitmap.CompressFormat.PNG, 50, bos)

                bos.writeTo(ostream)
                bos.close()
                ostream.close()
//                bitmap.recycle()

                image.close()
            }
        }, null)
        setupProjection(displayMetrics, reader.surface, object : VirtualDisplay.Callback() {
            override fun onResumed() {
                super.onResumed()
            }

            override fun onStopped() {
                super.onStopped()
            }

            override fun onPaused() {
                super.onPaused()
            }
        })

    }
}
