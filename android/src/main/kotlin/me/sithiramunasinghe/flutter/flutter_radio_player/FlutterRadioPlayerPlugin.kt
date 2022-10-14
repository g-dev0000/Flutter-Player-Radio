package me.sithiramunasinghe.flutter.flutter_radio_player

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.NonNull
import com.google.gson.Gson
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import me.sithiramunasinghe.flutter.flutter_radio_player.core.data.FRP_STOPPED
import me.sithiramunasinghe.flutter.flutter_radio_player.core.data.FRPAudioSource
import me.sithiramunasinghe.flutter.flutter_radio_player.core.events.FRPPlayerEvent
import me.sithiramunasinghe.flutter.flutter_radio_player.core.exceptions.FRPException
import me.sithiramunasinghe.flutter.flutter_radio_player.core.services.FRPCoreService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class FlutterRadioPlayerPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler {

    companion object {
        private const val TAG = "FlutterRadioPlayerPlugin"
        private const val METHOD_CHANNEL_NAME = "flutter_radio_player/method_channel"
        private const val EVENT_CHANNEL_NAME = "flutter_radio_player/event_channel"
        private val GSON = Gson()
    }

    var serviceIntent: Intent? = null

    private var isBound: Boolean = false
    private var context: Context? = null
    private var pluginActivity: Activity? = null
    private var frpChannel: MethodChannel? = null
    private var serviceConnection: ServiceConnection? = null

    private var eventSink: EventChannel.EventSink? = null
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null

    private lateinit var frpRadioPlayerService: FRPCoreService

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.flutterPluginBinding = flutterPluginBinding
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        Log.i(TAG, "::: Detaching FRP from FlutterEngine :::")
        this.context = null
        frpChannel?.setMethodCallHandler(null)
        frpChannel = null
        eventSink = null
        serviceIntent = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
//        val lifecycle: Lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding)
        Log.i(TAG, ":::: onAttachedToActivity ::::: ")
        EventBus.getDefault().register(this)
        pluginActivity = binding.activity
        startFRPService()
    }

    override fun onDetachedFromActivityForConfigChanges() {

    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivity() {
        if (isBound) {
            frpRadioPlayerService.onDestroy()
        }
        EventBus.getDefault().unregister(this)
    }

    private fun onAttachedToEngine(context: Context, binaryMessenger: BinaryMessenger) {
        Log.i(TAG, "::: Attaching to FRP to FlutterEngine :::")
        this.context = context
        frpChannel = MethodChannel(binaryMessenger, METHOD_CHANNEL_NAME)
        frpChannel?.setMethodCallHandler(this)

        val eventChannel = EventChannel(binaryMessenger, EVENT_CHANNEL_NAME)
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                Log.i(TAG, "EventChannel sink ok")
                eventSink = events
            }

            override fun onCancel(arguments: Any?) {
                Log.i(TAG, "EventChannel sink null")
            }
        })

        // service intent
        serviceIntent = Intent(context, FRPCoreService::class.java)

        // start the background service.
        pluginActivity?.startService(serviceIntent)

        // bind and get the service connection
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.i(TAG, "Service Connected...")
                val localBinder = service as FRPCoreService.LocalBinder
                frpRadioPlayerService = localBinder.service
                frpRadioPlayerService.currentActivity =
                    this@FlutterRadioPlayerPlugin.pluginActivity!!
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.i(TAG, "Service disconnected...")
                isBound = false
            }
        }

        context.bindService(serviceIntent, serviceConnection!!, Context.BIND_AUTO_CREATE)
    }

    @Subscribe
    fun handleFRPEvents(event: FRPPlayerEvent) {
        if (eventSink != null) {
            Log.d(TAG, "FRP Event data = $event")
            if (event.playbackStatus != null) {
                if (event.playbackStatus == FRP_STOPPED) {
                    Log.i(TAG, "Service unbind....")
                    isBound = false
                    context!!.unbindService(serviceConnection!!)
                }
            }
            eventSink?.success(GSON.toJson(event))
        } else {
            Log.i(TAG, "EventSink null")
        }
    }

    private fun startFRPService() {
        if (!isBound) {
            onAttachedToEngine(
                this.flutterPluginBinding!!.applicationContext,
                this.flutterPluginBinding!!.binaryMessenger
            )
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "init_service" -> {
                if (isBound) {
                    result.error("FRP_001", "Failed to call init_service", null)
                    throw FRPException("FRPCoreService already been initialized")
                }
                startFRPService()
                result.success("success")
            }
            "init_periodic_metadata" -> {
                if (!isBound) {
                    result.error("FRP_002", "Failed to call init_periodic_metadata", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                val period: Float = call.argument<Float>("milliseconds") ?: 30000F
                frpRadioPlayerService.initPeriodicMetaData(period)
                result.success("success")
            }
            "play" -> {
                if (!isBound) {
                    result.error("FRP_003", "Failed to call play", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                frpRadioPlayerService.play()
                result.success("success")
            }
            "pause" -> {
                if (!isBound) {
                    result.error("FRP_004", "Failed to call pause", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                frpRadioPlayerService.pause()
                result.success("success")
            }
            "play_or_pause" -> {
                if (!isBound) {
                    result.error("FRP_005", "Failed to call play_or_pause", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                frpRadioPlayerService.playOrPause()
                result.success("success")
            }
            "next_source" -> {
                if (!isBound) {
                    result.error("FRP_006", "Failed to call next_source", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                frpRadioPlayerService.nextMediaItem()
                result.success("success")
            }
            "previous_source" -> {
                if (!isBound) {
                    result.error("FRP_007", "Failed to call prev_source", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                frpRadioPlayerService.prevMediaItem()
                result.success("success")
            }
            "seek_source_to_index" -> {
                if (!isBound) {
                    result.error("FRP_008", "Failed to call prev_source", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                val sourceIndex: Int = call.argument<Int>("source_index") ?: 0
                val playIfReady: Boolean = call.argument<Boolean>("play_when_ready") ?: false
                frpRadioPlayerService.seekToMediaItem(sourceIndex, playIfReady)
                result.success("success")
            }
            "set_volume" -> {
                if (!isBound) {
                    result.error("FRP_009", "Failed to call set_volume", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                val volume: Float = call.argument<Float>("volume") ?: 0.5F
                frpRadioPlayerService.setVolume(volume)
                result.success("success")
            }
            "set_sources" -> {
                if (!isBound) {
                    result.error("FRP_010", "Failed to call set_sources", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }

                if (!call.hasArgument("media_sources")) {
                    result.error("FRP_011", "Failed to call set_sources", null)
                    throw FRPException("Invalid input")
                }

                val mediaSources = call.argument<ArrayList<HashMap<String, Any>>>("media_sources")

                if (mediaSources.isNullOrEmpty()) {
                    result.error("FRP_012", "Failed to call set_sources", null)
                    throw FRPException("Empty media sources")
                }

                val mappedSources = mediaSources.map { m -> FRPAudioSource.fromMap(m) }

                frpRadioPlayerService.setMediaSources(mappedSources, true)
                result.success("success")
            }
            "get_playback_state" -> {
                if (!isBound) {
                    result.error("FRP_013", "Failed to call set_sources", null)

                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                result.success(frpRadioPlayerService.getPlayerState())
            }
            "get_current_metadata" -> {
                if (!isBound) {
                    result.error("FRP_014", "Failed to call get_current_metadata", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                result.success(GSON.toJson(frpRadioPlayerService.getMetaData()))
            }
            "get_is_playing" -> {
                if (!isBound) {
                    result.error("FRP_015", "Failed to call get_is_playing", null)
                    throw FRPException("FRPCoreService has not been initialized yet")
                }
                result.success(frpRadioPlayerService.isPlaying())
            }
            else -> {
                result.notImplemented()
            }
        }
    }
}
