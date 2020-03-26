import 'dart:async';

import 'package:flutter/services.dart';

class FlutterRadioPlayer {
  static const MethodChannel _channel =
      const MethodChannel('flutter_radio_player');

  static const EventChannel _eventChannel =
      const EventChannel("flutter_radio_player_stream");

  // constants to support event channel
  static const flutter_radio_stopped = "flutter_radio_stopped";
  static const flutter_radio_playing = "flutter_radio_playing";
  static const flutter_radio_paused = "flutter_radio_paused";
  static const flutter_radio_error = "flutter_radio_error";
  static const flutter_radio_loading = "flutter_radio_loading";

  static Stream<String> _isPlayingStream;

  Future<void> init(String appName, String subTitle, String streamURL,
      String playWhenReady) async {
    return await _channel.invokeMethod("initService", {
      "appName": appName,
      "subTitle": subTitle,
      "streamURL": streamURL,
      "playWhenReady": playWhenReady
    });
  }

  Future<bool> play() async {
    return await _channel.invokeMethod("play");
  }

  Future<bool> pause() async {
    return await _channel.invokeMethod("pause");
  }

  Future<bool> playOrPause() async {
    print("Invoking platform method: playOrPause");
    return await _channel.invokeMethod("playOrPause");
  }

  Future<bool> stop() async {
    return await _channel.invokeMethod("stop");
  }

  Future<bool> isPlaying() async {
    bool isPlaying = await _channel.invokeMethod("isPlaying");
    return isPlaying;
  }

  Future<void> setVolume(double volume) async {
    await _channel.invokeMethod("setVolume", {"volume": volume});
  }

  /// Get the player stream.
  Stream<String> get isPlayingStream {
    if (_isPlayingStream == null) {
      _isPlayingStream =
          _eventChannel.receiveBroadcastStream().map<String>((value) => value);
    }
    return _isPlayingStream;
  }
}

/// Flutter_radio_playback status
enum PlaybackStatus {
  flutter_radio_stopped,
  flutter_radio_playing,
  flutter_radio_paused,
  flutter_radio_error,
  flutter_radio_loading,
}
