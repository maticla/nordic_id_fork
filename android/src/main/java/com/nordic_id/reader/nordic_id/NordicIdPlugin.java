package com.nordic_id.reader.nordic_id;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nordicid.nurapi.NurApi;

import java.util.HashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * NordicIdPlugin
 */
public class NordicIdPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener, NurListener {
    private MethodChannel channel;
    private static final String CHANNEL_Initialize = "Initialize";
    private static final String CHANNEL_Connect = "Connect";
    private static final String CHANNEL_Destroy = "Destroy";
    private static final String CHANNEL_StopTrace = "StopTrace";
    private static final String CHANNEL_Reset = "Reset";
    private static final String CHANNEL_PowerOff = "PowerOff";
    private static final String CHANNEL_RefreshTracing = "RefreshTracing";
    private static final String CHANNEL_IsConnected = "IsConnected";
    private static final String CHANNEL_ConnectionStatus = "ConnectionStatus";
    private static final String CHANNEL_TagsStatus = "TagsStatus";
    private static final String CHANNEL_ReadTag = "ReadTag";


    private static final PublishSubject<Boolean> connectionStatus = PublishSubject.create();
    private static final PublishSubject<String> tagsStatus = PublishSubject.create();

    Activity activity;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "nordic_id");
        channel.setMethodCallHandler(this);
        initReadEvent(flutterPluginBinding.getBinaryMessenger());
        initConnectionEvent(flutterPluginBinding.getBinaryMessenger());
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else {
            handleMethods(call, result);
        }
    }

    private void handleMethods(MethodCall call, Result result) {
        switch (call.method) {
            case CHANNEL_Initialize:
                init();
                result.success(true);
                break;
            case CHANNEL_Connect:
                NurHelper.getInstance().connect();
                result.success(true);
                break;
            case CHANNEL_IsConnected:
                final boolean isConnected = NurHelper.getInstance().isConnected();
                result.success(isConnected);
                break;
            case CHANNEL_Reset:
                NurHelper.getInstance().reset();
                result.success(true);
                break;
            case CHANNEL_PowerOff:
                NurHelper.getInstance().powerOff();
                result.success(true);
                break;
            case CHANNEL_StopTrace:
                NurHelper.getInstance().stopTrace();
                result.success(true);
                break;
            case CHANNEL_Destroy:
                NurHelper.getInstance().destroy();
                result.success(true);
                break;
            case CHANNEL_RefreshTracing:
                try {
                    if (NurHelper.getInstance().isTracingTag()) {
                        //Need to stop tag tracing
                        NurHelper.getInstance().stopTrace();
                        return;
                    }
                    NurHelper.getInstance().clearInventoryReadings(); //Clear all from old stuff
                    NurHelper.getInstance().doSingleInventory(); //Make single round inventory.
                } catch (Exception ex) {
                    Toast.makeText(activity, ex.getMessage(), Toast.LENGTH_LONG).show();
                    // NurHelper.getInstance().destroy();
                    result.success(false);
                }

                result.success(true);
                break;

            case CHANNEL_ReadTag:
                try {
                    String epcTag = call.argument("tag");
                    NurApi nurApi = NurHelper.GetNurApi();
                    byte[] targetEpcData = NurApi.hexStringToByteArray(epcTag);

                    HashMap<String, Object> readResult = new HashMap<>();

                    try {
                        // Read sensor data from USER memory bank
                        byte[] sensorData = nurApi.readTagByEpc(
                            targetEpcData,
                            targetEpcData.length,
                            NurApi.BANK_USER,
                            0x0E,  // Magnus S3 sensor data address
                            2      // Read 2 words (4 bytes) of sensor data
                        );
                        
                        // Convert bytes to sensor values
                        int rawData = ((sensorData[0] & 0xFF) << 8) | (sensorData[1] & 0xFF);
                        
                        // Extract temperature (10 bits)
                        int tempCode = (rawData >> 6) & 0x3FF;
                        double temperature = -40 + (0.1 * tempCode);
                        
                        // Extract humidity (10 bits)
                        int humidityCode = ((rawData & 0x3F) << 4) | ((rawData >> 12) & 0x0F);
                        double humidity = (0.1 * humidityCode);
                        
                        readResult.put("temperature", temperature);
                        readResult.put("humidity", humidity);
                        readResult.put("raw_data", NurApi.byteArrayToHexString(sensorData));
                        
                        result.success(readResult);
                    } catch (Exception ex) {
                        readResult.put("error_details", ex.getMessage());
                        result.error("SENSOR_READ_ERROR", ex.getMessage(), null);
                    }
                } catch (Exception ex) {
                    Toast.makeText(activity, "Failed to read tag: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    result.error("READ_ERROR", ex.getMessage(), null);
                }
        
                result.success(readResult);
            } catch (Exception ex) {
                    Toast.makeText(activity, "Failed to read tag: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    result.error("READ_ERROR", ex.getMessage(), null);
            }
                break;

            default:
                result.notImplemented();
        }
    }

    private static void initConnectionEvent(BinaryMessenger messenger) {
        final EventChannel connectionEventChannel = new EventChannel(messenger, CHANNEL_ConnectionStatus);
        connectionEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, final EventChannel.EventSink eventSink) {
                connectionStatus
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<Boolean>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(Boolean isConnected) {
                                eventSink.success(isConnected);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }

            @Override
            public void onCancel(Object o) {

            }
        });
    }

    private static void initReadEvent(BinaryMessenger messenger) {
        final EventChannel scannerEventChannel = new EventChannel(messenger, CHANNEL_TagsStatus);
        scannerEventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object o, final EventChannel.EventSink eventSink) {
                tagsStatus
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<String>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(String tag) {
                                eventSink.success(tag);
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            }

            @Override
            public void onCancel(Object o) {

            }
        });
    }


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
        this.activity = activityPluginBinding.getActivity();
        activityPluginBinding.addActivityResultListener(this);
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding activityPluginBinding) {
        this.activity = activityPluginBinding.getActivity();
        activityPluginBinding.addActivityResultListener(this);
    }

    public void init() {
        NurHelper.getInstance().init(activity);
        NurHelper.getInstance().initReading(this);
    }

    @Override
    public void onConnected(boolean isConnected) {
        connectionStatus.onNext(isConnected);
    }

    @Override
    public void onStopTrace() {
    }

    @Override
    public void onTraceTagEvent(int scaledRssi) {
    }

    @Override
    public void onClearInventoryReadings() {
    }

    @Override
    public void onInventoryResult(HashMap<String, String> tags,String jsonString) {
        if (tags != null)
            tagsStatus.onNext(jsonString);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }


    @Override
    public void onDetachedFromActivity() {
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        NurHelper.getInstance().onActivityResult(requestCode, resultCode, data);
        return true;
    }
}
