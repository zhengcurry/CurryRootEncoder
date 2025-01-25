package com.pedro.streamer.test

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.pedro.common.ConnectChecker
import com.pedro.library.base.recording.RecordController
import com.pedro.library.rtsp.RtspCamera2
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import com.pedro.streamer.R
import com.pedro.streamer.utils.PathUtils
import com.pedro.streamer.utils.toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * rtsp 实现仅录像，拍照，切换分辨率
 *
 * @author curry
 *
 * create on 2025/1/23
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class TestActivity : AppCompatActivity(), ConnectChecker {

    private val mediaPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        .toString() + File.separator + "test" + File.separator

    private lateinit var surfaceView: OpenGlView
//    private lateinit var surfaceView: SurfaceView
    val genericStream: RtspCamera2 by lazy {
        RtspCamera2(surfaceView,this).apply {
//            getStreamClient().setBitrateExponentialFactor(0.5f)
            getStreamClient().setOnlyVideo(true)
            getStreamClient().getCacheSize()

        }
    }
    private lateinit var bStartStop: ImageView
    private lateinit var txtBitrate: TextView
    private var width = 2560
    private var height = 1440
    private val vBitrate = 3500 * 1000
    private val aBitrate = 128 * 1000
    private var recordPath = ""

    //Bitrate adapter used to change the bitrate on fly depend of the bandwidth.
    private val bitrateAdapter = BitrateAdapter {
        genericStream.setVideoBitrateOnFly(it)
    }.apply {
        setMaxBitrate(vBitrate + aBitrate)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test)

        bStartStop = findViewById(R.id.b_start_stop)
        val bRecord = findViewById<ImageView>(R.id.b_record)
        val bSwitchCamera = findViewById<ImageView>(R.id.switch_camera)

        findViewById<AppCompatButton>(R.id.btn_take_pic).setOnClickListener {
//            surfaceView.takePhoto {
//                runOnUiThread {
//                    Log.e("curry", "onCreate: ")
//                    findViewById<ImageView>(R.id.iv_pic).setImageBitmap(it)
//                }
//            }
            genericStream.glInterface.takePhoto{
                runOnUiThread {
                    Log.e("curry", "onCreate: ")
                    findViewById<ImageView>(R.id.iv_pic).setImageBitmap(it)
                }
            }
        }
        findViewById<AppCompatButton>(R.id.btn_change_size).setOnClickListener {
            val wasOnPreview = genericStream.isOnPreview
            genericStream.stopStream()
            width = 1080
            height = 1920
            prepare()
            if (wasOnPreview) genericStream.startPreview()
        }

        txtBitrate = findViewById(R.id.txt_bitrate)
        surfaceView = findViewById(R.id.surfaceView)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!genericStream.isOnPreview) genericStream.startPreview()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (genericStream.isOnPreview) genericStream.stopPreview()
            }

        })

        bStartStop.setOnClickListener {
            if (!genericStream.isStreaming) {
                genericStream.startStream("rtsp://127.0.0.1:8554/live/test")
                bStartStop.setImageResource(R.drawable.stream_stop_icon)
            } else {
                genericStream.stopStream()
                bStartStop.setImageResource(R.drawable.stream_icon)
            }
        }
        bRecord.setOnClickListener {
            if (!genericStream.isRecording) {
                val folder = PathUtils.getRecordPath()
                if (!folder.exists()) folder.mkdir()
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                recordPath = "${folder.absolutePath}/${sdf.format(Date())}.mp4"
                genericStream.startRecord(recordPath) { status ->
                    if (status == RecordController.Status.RECORDING) {
                        bRecord.setImageResource(R.drawable.stop_icon)
                    }
                }
                bRecord.setImageResource(R.drawable.pause_icon)
            } else {
                genericStream.stopRecord()
                bRecord.setImageResource(R.drawable.record_icon)
                PathUtils.updateGallery(this, recordPath)
            }
        }


        prepare()
        genericStream.getStreamClient().setReTries(10)
    }


    private fun prepare() {
        val prepared = try {
            genericStream.prepareVideo(width, height, vBitrate)
        } catch (e: IllegalArgumentException) {
            false
        }
        if (!prepared) {
            toast("Audio or Video configuration failed")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        genericStream.stopStream()
    }

    override fun onConnectionStarted(url: String) {
    }

    override fun onConnectionSuccess() {
        toast("Connected")
    }

    override fun onConnectionFailed(reason: String) {
        if (genericStream.getStreamClient().reTry(5000, reason, null)) {
            toast("Retry")
        } else {
            genericStream.stopStream()
            bStartStop.setImageResource(R.drawable.stream_icon)
            toast("Failed: $reason")
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        bitrateAdapter.adaptBitrate(bitrate, genericStream.getStreamClient().hasCongestion())
        txtBitrate.text = String.format(Locale.getDefault(), "%.1f mb/s", bitrate / 1000_000f)
    }

    override fun onDisconnect() {
        txtBitrate.text = String()
        toast("Disconnected")
    }

    override fun onAuthError() {
        genericStream.stopStream()
        bStartStop.setImageResource(R.drawable.stream_icon)
        toast("Auth error")
    }

    override fun onAuthSuccess() {
        toast("Auth success")
    }

}