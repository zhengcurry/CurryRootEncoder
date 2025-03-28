package com.pedro.streamer.test

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.BufferVideoSource
import com.pedro.library.base.recording.RecordController
import com.pedro.library.rtsp.RtspStream
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import com.pedro.streamer.R
import com.pedro.streamer.utils.PathUtils
import com.common.util.YuvConverter
import com.pedro.streamer.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
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
class TestSendCustomDataActivity : AppCompatActivity(), ConnectChecker {

    private val mediaPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        .toString() + File.separator + "test" + File.separator

    private var width = 384 * 4
    private var height = 288 * 4
    private val vBitrate = 6 * 1000 * 1000
    private val aBitrate = 128 * 1000
    private val sampleRate = 32000
    private val isStereo = true
    private val yuvConverter: YuvConverter =
        YuvConverter(width, height)

    private lateinit var surfaceView: OpenGlView
    private val videoSource: BufferVideoSource =
        BufferVideoSource(BufferVideoSource.Format.NV12, vBitrate)

    //    private lateinit var surfaceView: SurfaceView
    val genericStream: RtspStream by lazy {
        RtspStream(
            this,
            this,
            videoSource,
            NoAudioSource()
        ).apply {
//            getStreamClient().setBitrateExponentialFactor(0.5f)
            getStreamClient().setOnlyVideo(true)
            getStreamClient().getCacheSize()
        }
    }
    private lateinit var bStartStop: ImageView
    private lateinit var txtBitrate: TextView
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

        findViewById<AppCompatButton>(R.id.btn_change_size).visibility = View.GONE

        txtBitrate = findViewById(R.id.txt_bitrate)
        surfaceView = findViewById(R.id.surfaceView)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!genericStream.isOnPreview) genericStream.startPreview(surfaceView, false)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (genericStream.isOnPreview) genericStream.stopPreview()
            }

        })

        bStartStop.setOnClickListener {
            if (!genericStream.isStreaming) {
//                genericStream.startStream("rtsp://127.0.0.1:8554/live/test")
                genericStream.startStream("rtsp://192.168.189.135:8554/live/test")
//                genericStream.startStream("rtsp://172.16.42.111:8554/live/test")
                bStartStop.setImageResource(R.drawable.stream_stop_icon)

                flag = true
                sendDataPrepare()
            } else {
                flag = false
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

    private var time = System.currentTimeMillis()
    private var flag = false
    private val FRAME_SPACE_TIME = 20
    private fun sendDataPrepare() {
        CoroutineScope(Dispatchers.IO).launch {
            var index = 0
            while (flag) {
                Log.e("curry", "sendDataPrepare: 1")
                time = System.currentTimeMillis()
                index++
                if (index > 200) {
                    index = 1
                }
                Log.e("curry", "sendDataPrepare: 2")
                val src: ByteArray = readArgbFileToIntArray(
                    this@TestSendCustomDataActivity,
//                    File("sdcard/test_x3_4x_data/$index.bin")
                    File("sdcard/test_x3_4x_data_wave/" + String.format("%04d", index) + ".bin")
                )
                Log.e("curry", "sendDataPrepare: 3")

                if (src != null) {
                    videoSource.setBuffer(src)
                }
                time = System.currentTimeMillis() - time
                delay(
                    if (FRAME_SPACE_TIME - time > 0) FRAME_SPACE_TIME - time else 0
                )
            }
        }
    }

    fun readArgbFileToIntArray(context: Context, file: File): ByteArray {
        Log.e("curry", "readArgbFileToIntArray: 1")

        /**
         * 零拷贝读取：
         * 使用 MemoryFile 或 FileChannel.map 将文件映射到内存，直接生成 ByteBuffer：
         */
        val fis = FileInputStream(file)
        val channel = fis.channel
        val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())

        // 打开 Asset 文件并读取字节数据
//        val inputStream = context.assets.open(fileName)
//
//        val bytes = inputStream.use { it.readBytes() } // 自动关闭流
//
//        // 检查文件大小是否为4的倍数
//        require(bytes.size % 4 == 0) { "文件大小必须是4字节的倍数" }

        // 使用 ByteBuffer 转换字节数组到 IntArray
//        val buffer = ByteBuffer.wrap(bytes).apply {
//            order(ByteOrder.BIG_ENDIAN) // 根据ARGB的字节顺序设置
//        }

//        Log.e("curry", "readArgbFileToIntArray: 2")
//        // 创建 IntArray 并填充数据
//        val intArray = IntArray(buffer.remaining() / 4)
//        buffer.asIntBuffer().get(intArray)
//
//        Log.e("curry", "readArgbFileToIntArray: 3")
//
//        // 5. 将每个Int从 0xRRGGBBAA 转换为 0xAARRGGBB
//        for (i in intArray.indices) {
//            intArray[i] = (intArray[i] shl 24) or (intArray[i] ushr 8)
//        }
//
////        YUVUtil.ARGBtoYUV420SemiPlanar(intArray,width,height)
//        Log.e("curry", "readArgbFileToIntArray: 4")

        Log.e("curry", "readArgbFileToIntArray: 2")
        val byteArray = ByteArray(buffer.remaining())
        buffer.get(byteArray)
        Log.e("curry", "readArgbFileToIntArray: 3")
        return yuvConverter.RGBAToNV12(byteArray, width, height, false, 0)
    }

    private fun prepare() {
        val prepared = try {
            genericStream.prepareVideo(width, height, vBitrate, 50, 2, 0) && genericStream.prepareAudio(
                sampleRate,
                isStereo,
                aBitrate
            )
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