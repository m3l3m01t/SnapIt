package com.github.m3l3m01t.snapit

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import java.nio.ByteBuffer


class OverlayWidgetService : Service() {
    private lateinit var windowManager: WindowManager

    private lateinit var snapWidget: ImageView

    private var widgetShown = false

    private lateinit var displayManager: DisplayManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        if (!widgetShown) {
            widgetShown = true

            showWidget()
        }
    }

    override fun onDestroy() {
        if (snapWidget != null) {
            windowManager.removeView(snapWidget)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun showWidget() {
        snapWidget = ImageView(this)
        snapWidget.setImageResource(R.mipmap.ic_launcher_round)

        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT)

        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = 0
        params.y = 100

        //this code is for dragging the chat head
        snapWidget.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = params.x
            private var initialY: Int = params.y

            private var initialTouchX: Float = 0.toFloat()
            private var initialTouchY: Float = 0.toFloat()
            private var dragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y

                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        dragging = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            v.performClick()
                        }
                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        dragging = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if ((event.rawX - initialTouchX > 10) || (event.rawY - initialTouchY > 10)) {
                            dragging = true
                        }

                        if (dragging) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()

                            windowManager.updateViewLayout(snapWidget, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        snapWidget.setOnClickListener {
            /*            AlertDialog.Builder(this).setTitle("Welcome")
                                .setMessage("Hello World!").setIcon(android.R.drawable.ic_dialog_info)
                                .create().show()*/
//            (application as SnapApplication).takeSnapshot()

            if (mediaCodec == null) {

//                startRecord()
            }
//            startActivity(Intent(this, SettingsActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        windowManager.addView(snapWidget, params)

    }

    private fun createEncoder(width: Int, height: Int): MediaCodec {
        val mediaCodec = MediaCodec.createEncoderByType("video/avc")

        val mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 125000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)

        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        return mediaCodec
    }

    var mediaCodec: MediaCodec? = null

    private fun startRecord() {
        var displayMetrics = DisplayMetrics()

        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels,
                PixelFormat.RGBA_8888, 4)

        val virtualDisplay = displayManager.createVirtualDisplay("virtual",
                displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi,
                imageReader!!.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)

        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {

            }

            override fun onDisplayAdded(displayId: Int) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onDisplayRemoved(displayId: Int) {
                if (displayManager.getDisplay(displayId) == virtualDisplay.display) {

                }
            }

        }, null)
        mediaCodec = createEncoder(640, 480)

        if (mediaCodec != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
/*                mediaCodec!!.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {

                    }

                    override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {

                    }

                    override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {

                    }

                    override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {

                    }
                })*/

            }

            mediaCodec!!.start()
            imageReader.setOnImageAvailableListener({
                reader ->

                val image = reader.acquireLatestImage()
                if (image != null) {
                    val inputBuffers = mediaCodec!!.inputBuffers
//                    val outputBuffers = mediaCodec!!.getOutputBuffers();

                    image.planes.forEach { plane ->
                        val inputBufferIndex = mediaCodec!!.dequeueInputBuffer(-1)
                        if (inputBufferIndex >= 0) {
                            var inputBuffer: ByteBuffer = inputBuffers [inputBufferIndex]
                            inputBuffer.clear()
                            inputBuffer.put(plane.buffer)

                            mediaCodec!!.queueInputBuffer(inputBufferIndex, 0, inputBuffer.position(), 0, 0)

                            val bufferInfo = MediaCodec.BufferInfo()

                            mediaCodec!!.dequeueOutputBuffer(bufferInfo, 5000)

                            println(bufferInfo)
                        }
                    }
                }

            }, null)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return START_STICKY
    }
}
