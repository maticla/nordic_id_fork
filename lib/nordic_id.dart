import 'package:flutter/services.dart';

//import 'nordic_id_platform_interface.dart';

class NordicId {
  static const _channel = MethodChannel('nordic_id');

/*   Future<String?> getPlatformVersion() {
    return NordicIdPlatform.instance.getPlatformVersion();
  }
 */

  static Future<String?> getPlatformVersion() async {
    final version = await _channel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  static const EventChannel tagsStatusStream = EventChannel('TagsStatus');
  static const EventChannel connectionStatusStream = EventChannel('ConnectionStatus');

  static Future<bool?> get initialize async {
    return _channel.invokeMethod('Initialize');
  }

  static Future<bool?> get connect async {
    return _channel.invokeMethod('Connect');
  }

  static Future<bool?> get isConnected async {
    return _channel.invokeMethod('IsConnected');
  }

  static Future<bool?> get destroy async {
    return _channel.invokeMethod('destroy');
  }

  static Future<bool?> get stopTrace async {
    return _channel.invokeMethod('StopTrace');
  }

  static Future<bool?> get reset async {
    return _channel.invokeMethod('Reset');
  }

  static Future<bool?> get powerOff async {
    return _channel.invokeMethod('PowerOff');
  }

  static Future<bool?> get refreshTracing async {
    return _channel.invokeMethod('RefreshTracing');
  }

  static Future<dynamic> readTag(String tagId) async {
    return _channel.invokeMethod('ReadTag', {'tag': tagId});
  }

  /* static Future<bool?> setWorkArea(String value) async {
    return _channel
        .invokeMethod('setWorkArea', <String, String>{'value': value});
  } */
}
