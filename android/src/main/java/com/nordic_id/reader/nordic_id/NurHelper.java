package com.nordic_id.reader.nordic_id;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nordicid.nurapi.AccConfig;
import com.nordicid.nurapi.AccSensorConfig;
import com.nordicid.nurapi.AccessoryExtension;
import com.nordicid.nurapi.BleScanner;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurApiAndroid;
import com.nordicid.nurapi.NurApiAutoConnectTransport;
import com.nordicid.nurapi.NurApiErrors;
import com.nordicid.nurapi.NurApiException;
import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurDeviceListActivity;
import com.nordicid.nurapi.NurDeviceSpec;
import com.nordicid.nurapi.NurEventAutotune;
import com.nordicid.nurapi.NurEventClientInfo;
import com.nordicid.nurapi.NurEventDeviceInfo;
import com.nordicid.nurapi.NurEventEpcEnum;
import com.nordicid.nurapi.NurEventFrequencyHop;
import com.nordicid.nurapi.NurEventIOChange;
import com.nordicid.nurapi.NurEventInventory;
import com.nordicid.nurapi.NurEventNxpAlarm;
import com.nordicid.nurapi.NurEventProgrammingProgress;
import com.nordicid.nurapi.NurEventTagTrackingChange;
import com.nordicid.nurapi.NurEventTagTrackingData;
import com.nordicid.nurapi.NurEventTraceTag;
import com.nordicid.nurapi.NurEventTriggeredRead;
import com.nordicid.nurapi.NurRespDevCaps;
import com.nordicid.nurapi.NurRespReadData;
import com.nordicid.nurapi.NurRespReaderInfo;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;
import com.nordicid.nurapi.BleScanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import nordicid.com.nurupdate.NurDeviceUpdate;
import nordicid.com.nurupdate.NurUpdateParams;

public class NurHelper {
    private static NurHelper instance;

    private NurHelper() {
    }

    public static NurHelper getInstance() {
        if (instance == null)
            instance = new NurHelper();
        return instance;
    }

    public static final String TAG = "NUR_Helper"; //Can be used for filtering Log's at Logcat
    public Activity context;
    private final int APP_PERMISSION_REQ_CODE = 41;
    //Need to keep track connection state with NurApi IsConnected
    private boolean mIsConnected;

    private static NurApi mNurApi;
    private static AccessoryExtension mAccExt; //accessories of reader like barcode scanner, beeper, vibration..
    static boolean mShowingSmartPair = false;
    static boolean mAppPaused = false;
    private NurApiAutoConnectTransport hAcTr;

    //In here found tags stored
    private NurTagStorage mTagStorage = new NurTagStorage();

    //Controller of Trace tag
    private TraceTagController mTraceController;
    private NurListener mNurListener;
    int mLastVal = 0;

    //Selected EPC to Trace
    static String mSelectedEpc;

    //These values will be shown in the UI
    private String mUiConnStatusText;
    private String mUiConnButtonText;

    public static NurApi GetNurApi() {
        return mNurApi;
    }

    public static AccessoryExtension GetAccessoryExtensionApi() {
        return mAccExt;
    }

    //When connected, this flag is set depending if Accessories like barcode scan, beep etc supported.
    private static boolean mIsAccessorySupported;

    public static boolean IsAccessorySupported() {
        return mIsAccessorySupported;
    }


    public void requestBluetoothPermission() {
        /** Bluetooth Permission checks **/
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.BLUETOOTH_ADMIN) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.BLUETOOTH_CONNECT) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.BLUETOOTH_SCAN) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {

            } else {
                ActivityCompat.requestPermissions(context, new String[]{
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.BLUETOOTH_ADMIN,
                                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                android.Manifest.permission.BLUETOOTH_SCAN,
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        APP_PERMISSION_REQ_CODE);
            }
        }
    }

    public void init(Activity context) {
        this.context = context;
        requestBluetoothPermission();

        //Bluetooth LE scanner need to find EXA's near
        BleScanner.init(context);

        mIsConnected = false;

        //Create NurApi handle.
        mNurApi = new NurApi();

        //Accessory extension contains device specific API like barcode read, beep etc..
        //This included in NurApi.jar
        mAccExt = new AccessoryExtension(mNurApi);

        // In this activity, we use mNurApiListener for receiving events
        mNurApi.setListener(mNurApiListener);

        mUiConnStatusText = "Disconnected!";
        mUiConnButtonText = "CONNECT";
    }

    public void initReading(NurListener nurListener) {
        setNurListener(nurListener);
        mTraceController = new TraceTagController(mNurApi);
        mTraceController.setListener(new TraceTagController.TraceTagListener() {
            @Override
            public void traceTagEvent(TraceTagController.TracedTagInfo data) {
                int scaledRssi = data.scaledRssi;
                mNurListener.onTraceTagEvent(scaledRssi);
                mLastVal = scaledRssi;
            }

            @Override
            public void readerDisconnected() {
                stopTrace();
            }

            @Override
            public void readerConnected() {
            }

            @Override
            public void IOChangeEvent(NurEventIOChange event) {
            }

        });
    }

    public int getLastRSSIValue() {
        return mLastVal;
    }

    public void setLastRSSIValue(int rssiValue) {
        mLastVal = rssiValue;
    }

    /**
     * Is tracing/reading tags.
     */
    public boolean isTracingTag() {
        return mTraceController.isTracingTag();
    }

    /**
     * Stop tracing.
     */
    public void stopTrace() {
        if (mTraceController.isTracingTag()) {
            mTraceController.stopTagTrace();
            mNurListener.onStopTrace();
        }
    }

    /**
     * Start tracing. EPC has been selected from list.
     */
    public String startTrace() {
        try {
            if (!mTraceController.isTracingTag()) {
                if (mSelectedEpc == null || mSelectedEpc.length() == 0)
                    return "Select EPC to locate from list";
                else if (!mNurApi.isConnected())
                    return "Reader not connected";
                else if (mTraceController.startTagTrace(mSelectedEpc))
                    return "";
                else
                    return "Invalid EPC";
            }
        } catch (Exception ex) {
            return "Reader error";
        }

        return "";
    }

    //Set tag to be traced
    public void setTagTrace(String traceTagEPC) {
        mSelectedEpc = traceTagEPC;
        mTraceController.setTagTrace(mSelectedEpc);

    }

    /**
     * Clear tag storages from NUR and from our internal tag storage
     * Also Listview cleared
     */
    public void clearInventoryReadings() {
        mNurApi.getStorage().clear();
        mTagStorage.clear();
        mNurListener.onClearInventoryReadings();
    }

    /**
     * Perform inventory to seek tags near
     */
    public boolean doSingleInventory() throws Exception {

        if (!mNurApi.isConnected())
            return false;

        // Make sure antenna autoswitch is enabled
        if (mNurApi.getSetupSelectedAntenna() != NurApi.ANTENNAID_AUTOSELECT)
            mNurApi.setSetupSelectedAntenna(NurApi.ANTENNAID_AUTOSELECT);

        // Clear old readings
        clearInventoryReadings();

        try {
            // Perform inventory
            mNurApi.inventory();
            // Fetch tags from NUR
            mNurApi.fetchTags();
        } catch (NurApiException ex) {
            // Did not get any tags
            if (ex.error == NurApiErrors.NO_TAG)
                return true;

            throw ex;
        }
        // Handle inventoried tags
        handleInventoryResult();
        return true;
    }

    /**
     * New tags will be added to our existing tag storage.
     * List view adapter will be updated for new tags
     */
    public void handleInventoryResult() {
        synchronized (mNurApi.getStorage()) {
            HashMap<String, String> tmp;
            NurTagStorage tagStorage = mNurApi.getStorage();

            // Add tags tp internal tag storage
            for (int i = 0; i < tagStorage.size(); i++) {
                JSONObject json = new JSONObject();
                NurTag tag = tagStorage.get(i);

                final JSONArray jsonArray = new JSONArray();

                int xpcW2 = tag.getXPC_W2();
                Log.d("XPCW2_RAW", String.valueOf(xpcW2));

// Let's see the full hex value
                Log.d("XPCW2_HEX", String.format("0x%04X", xpcW2 & 0xFFFF));

// Parse according to specification
                int sensorType = (xpcW2 >> 12) & 0xF;    // First 4 bits
                int dataType = (xpcW2 >> 10) & 0x3;      // Next 2 bits
                int sensorValue = xpcW2 & 0x3FF;         // Last 10 bits

// If it's a negative value (bit 9 is set)
                if ((sensorValue & 0x200) != 0) {
                    sensorValue = -(((~sensorValue) & 0x3FF) + 1);
                }

                Log.d("XPCW2_PARSED", String.format(
                        "SensorType: %d, DataType: %d, Value: %d",
                        sensorType, dataType, sensorValue
                ));

                if (mTagStorage.addTag(tag)) {
                    // Add new
                    tmp = new HashMap<String, String>();
                    tmp.put("epc", tag.getEpcString());
                    tmp.put("rssi", Integer.toString(tag.getRssi()));
                    tag.setUserdata(tmp);
                    try {
                        json.put("epc", tag.getEpcString());
                        json.put("rssi", Integer.toString(tag.getRssi()));
                        jsonArray.put(json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    mNurListener.onInventoryResult(tmp, jsonArray.toString());
                }
            }

            // Clear NurApi tag storage
            tagStorage.clear();
            //Beeper.beep(Beeper.BEEP_40MS);
        }
    }

    public String getConnectButtonText() {
        return mUiConnButtonText;
    }

    public void setNurListener(NurListener nurListener) {
        this.mNurListener = nurListener;
    }

    public void reset() {
        // In this activity, we use mNurApiListener for receiving events
        mAppPaused = false;
        mNurApi.setListener(mNurApiListener);
        if (!mNurApi.isConnected() && mIsConnected) {
            mNurApiListener.disconnectedEvent();
            mUiConnButtonText = "CONNECT";
        }

        if (!mShowingSmartPair && hAcTr != null) {
            String clsName = hAcTr.getClass().getSimpleName();
            if (clsName.equals("NurApiSmartPairAutoConnect")) {
                mShowingSmartPair = showSmartPairUI();
            }
        } else {
            mShowingSmartPair = false;
        }
    }

    public void onStop() {
        mShowingSmartPair = false;
    }

    public void destroy() {
        //Kill connection when app killed
        if (hAcTr != null) {
            hAcTr.onDestroy();
            hAcTr = null;
        }
    }


    /**
     * NurApi event handlers.
     * NOTE: All NurApi events are called from NurApi thread, thus direct UI updates are not allowed.
     * If you need to access UI controls, you can use runOnUiThread(Runnable) or Handler.
     */
    private final NurApiListener mNurApiListener = new NurApiListener() {
        @Override
        public void triggeredReadEvent(NurEventTriggeredRead event) {
            Log.i("NUR_API", "Triggered read event!");
        }

        @Override
        public void traceTagEvent(NurEventTraceTag event) {
        }

        @Override
        public void programmingProgressEvent(NurEventProgrammingProgress event) {
        }

        @Override
        public void nxpEasAlarmEvent(NurEventNxpAlarm event) {
        }

        @Override
        public void logEvent(int level, String txt) {
        }

        @Override
        public void inventoryStreamEvent(NurEventInventory event) {
        }

        @Override
        public void inventoryExtendedStreamEvent(NurEventInventory event) {
        }

        @Override
        public void frequencyHopEvent(NurEventFrequencyHop event) {
        }

        @Override
        public void epcEnumEvent(NurEventEpcEnum event) {
        }

        @Override
        public void disconnectedEvent() {
            mIsConnected = false;
            Log.i(TAG, "Disconnected!");
            mNurListener.onConnected(false);
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "Reader disconnected", Toast.LENGTH_SHORT).show();
                    showConnecting();

                    // Show smart pair ui
                    if (!mShowingSmartPair && hAcTr != null) {
                        String clsName = hAcTr.getClass().getSimpleName();
                        if (clsName.equals("NurApiSmartPairAutoConnect")) {
                            mShowingSmartPair = showSmartPairUI();
                        }
                    } else {
                        mShowingSmartPair = false;
                    }
                }
            });
        }

        @Override
        public void deviceSearchEvent(NurEventDeviceInfo event) {
        }

        @Override
        public void debugMessageEvent(String event) {
        }

        @Override
        public void connectedEvent() {
            //Device is connected.
            // Let's find out is device provided with accessory support (Barcode reader, battery info...) like EXA
            try {
                if (mAccExt.isSupported()) {
                    //Yes. Accessories supported
                    mIsAccessorySupported = true;
                    //Let's take name of device from Accessory api
                    mUiConnStatusText = "Connected to " + mAccExt.getConfig().name;
                } else {
                    //Accessories not supported. Probably fixed reader.
                    mIsAccessorySupported = false;
                    NurRespReaderInfo ri = mNurApi.getReaderInfo();
                    mUiConnStatusText = "Connected to " + ri.name;
                }
            } catch (Exception ex) {
                mUiConnStatusText = ex.getMessage();
            }

            mIsConnected = true;
            Log.i(TAG, "Connected!");
            //Beeper.beep(Beeper.BEEP_100MS);
            mNurListener.onConnected(true);

            //amr
            //mUiConnStatusTextColor = Color.GREEN;
            mUiConnButtonText = "DISCONNECT";
            //amr
            //showOnUI();
        }

        @Override
        public void clientDisconnectedEvent(NurEventClientInfo event) {
        }

        @Override
        public void clientConnectedEvent(NurEventClientInfo event) {
        }

        @Override
        public void bootEvent(String event) {
        }

        @Override
        public void IOChangeEvent(NurEventIOChange event) {

            Log.i(TAG, "Key " + event.source);

            // trigger button
            if(event.source == 100) {
                try {
                    NurRespReadData data = mNurApi.scanSingleTag();
//                    Log.d("SCANNER", data.toString());
//                    Log.d("SCANNER", data.epcStr);
//                    Log.d("SCANNER", Arrays.toString(data.epc));
//                    Log.d("SCANNER", data.epcStr);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void autotuneEvent(NurEventAutotune event) {
        }

        @Override
        public void tagTrackingScanEvent(NurEventTagTrackingData event) {
        }

        //@Override
        public void tagTrackingChangeEvent(NurEventTagTrackingChange event) {
        }
    };

    public NurApiListener getNurApiListener() {
        return mNurApiListener;
    }

    boolean showSmartPairUI() {
        if (mNurApi.isConnected() || mAppPaused)
            return false;

        try {
            Log.d(TAG, "showSmartPairUI()");
            Intent startIntent = new Intent(context, Class.forName("com.nordicid.smartpair.SmartPairConnActivity"));
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(startIntent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    void showConnecting() {
        if (hAcTr != null) {
            mUiConnStatusText = "Connecting to " + hAcTr.getAddress();
            //amr
            // mUiConnStatusTextColor = Color.YELLOW;
        } else {
            mUiConnStatusText = "Disconnected";
            //amr
            // mUiConnStatusTextColor = Color.RED;
            mUiConnButtonText = "CONNECT";
        }
        //amr
        // showOnUI();
    }

    boolean showNurUpdateUI() {
        try {
            Log.d(TAG, "showNurUpdateUI()");
            Intent startIntent = new Intent(context, Class.forName("nordicid.com.nurupdate.NurDeviceUpdate"));
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(startIntent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Show Sensors Page
     */
    public void showSensors() {
        try {
            if (mNurApi.isConnected()) {
                if (IsAccessorySupported()) {
                    //There is accessories but is there sensors like ToF..
                    ArrayList<AccSensorConfig> sensorList = mAccExt.accSensorEnumerate();

                    if (sensorList.size() > 0) {
                        //Intent sensorIntent = new Intent(context, Sensor.class);
                        //context.startActivityForResult(sensorIntent, 0);
                    } else
                        Toast.makeText(context, "No sensors", Toast.LENGTH_LONG).show();
                } else
                    Toast.makeText(context, "Sensors not supported!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Reader not connected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Device firmware update from local zip file or from Nordic ID server
     * Update packets are uncompressed zip files containing firmware files and UpdateInfo.json file.
     * UpdateInfo.json described files and versions
     */
    public void updateFirmware(int selection) {

        try {
            if (mNurApi.isConnected()) {

                //Set parameters for Update job
                NurUpdateParams updateParams = new NurUpdateParams();
                //NurApi instance
                updateParams.setNurApi(mNurApi);
                //Possible Nur accessory instance
                updateParams.setAccessoryExtension(GetAccessoryExtensionApi());
                //If we are connected to device via Bluetooth, give current ble address.
                updateParams.setDeviceAddress(hAcTr.getAddress());

                /**
                 * Zip path string may be empty or URL or Uri
                 * Empty zipPath allow users to browse zip file from local file system
                 */

                try {
                    if (selection == 0) {
                        //Force user to select zip from the filesystem
                        updateParams.setZipPath("");
                    } else if (selection == 1) {
                        //Load from Nordic ID server.
                        updateParams.setZipPath("https://raw.githubusercontent.com/NordicID/nur_firmware/master/zip/NIDLatestFW.zip");
                    } else
                        return;

                    //Update params has been given. Show update Intent
                    showNurUpdateUI();
                } catch (Exception e) {
                    Toast.makeText(context, "Error loading ZIP " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(context, "Reader not connected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Handle barcode scan click. Start Barcode activity (only if reader support acessories). See Barcode.java
     */
    public void showBarcodePage() {
        try {
            if (mNurApi.isConnected()) {
                if (IsAccessorySupported()) {
                    //Accessories is supported but all devices doesn't have barcode scanner
                    AccConfig cfg = mAccExt.getConfig();
                    if (!cfg.hasImagerScanner()) {
                        Toast.makeText(context, "Barcode not supported!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    //Yes, barcode scanner found. Show activity to play with scanner.
                    //Intent barcodeIntent = new Intent(context, Barcode.class);
                    //context.startActivityForResult(barcodeIntent, 0);
                } else
                    Toast.makeText(context, "No accessories on device!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Reader not connected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Handle inventory click. Start Inventory activity. See inventory.java
     */
    public void showInventoryPage() {
        try {
            if (mNurApi.isConnected()) {
                //Intent inventoryIntent = new Intent(context, Inventory.class);
                //context.startActivityForResult(inventoryIntent, 0);
            } else {
                Toast.makeText(context, "Reader not connected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Handle tag write click. Start Write Tag activity. See Write.java
     */
    public void onWriteTagPage() {
        try {
            if (mNurApi.isConnected()) {
                //Intent writeTagIntent = new Intent(context, WriteTag.class);
                // context.startActivityForResult(writeTagIntent, 0);
            } else {
                Toast.makeText(context, "Reader not connected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Handle tag trace click. Start Write Tag activity. See Trace.java
     */
    public void onTracePage() {
        try {
            if (mNurApi.isConnected()) {
                //Intent traceIntent = new Intent(context, Trace.class);
                //context.startActivityForResult(traceIntent, 0);
            } else {
                Toast.makeText(context, "Reader not connected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Handle power off click.
     * Sends PowerOff command to reader.
     */
    public void powerOff() {
        try {
            if (mNurApi.isConnected()) {

                if (IsAccessorySupported()) { //Only device with accessory can be power off by command
                    mAccExt.powerDown(); //Power off device
                    Toast.makeText(context, "Device Power OFF!", Toast.LENGTH_LONG).show();
                } else Toast.makeText(context, "PowerOff not supported!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Reader not connected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
        }

    }

    /**
     * Handle reader connection button click.
     * First is check if Bluetooth adapter is ON or OFF.
     * Then Bluetooth scan is performed to search devices from near.
     * User can select device from list to connect.
     * It's useful to store last connected device MAC to persistent memory inorder to reconnect later on to same device without selecting from list. This demo doesn't do MAC storing.
     */
    public void connect() {
        if (mNurApi.isConnected()) {
            hAcTr.dispose();
            hAcTr = null;
        } else {
            Toast.makeText(context, "Start searching. Make sure device power ON!", Toast.LENGTH_LONG).show();
            NurDeviceListActivity.startDeviceRequest(context, mNurApi);
        }
    }

    public boolean isConnected() {
        return mNurApi != null && mNurApi.isConnected();
    }

    public String getFileVersion() {
        return mNurApi.getFileVersion();
    }

    public NurRespReaderInfo getReaderInfo() throws Exception {
        return mNurApi.getReaderInfo();
    }

    public NurRespDevCaps getDeviceCaps() throws Exception {
        return mNurApi.getDeviceCaps();
    }

    public String getNurApiAndroidVersion() {
        return NurApiAndroid.getVersion();
    }

    public String getNurDeviceUpdateVersion() {
        return NurDeviceUpdate.getVersion();
    }

    public String getSecondaryVersion() throws Exception {
        return mNurApi.getVersions().secondaryVersion;
    }

    public String getFrameworkFullApplicationVersion() throws Exception {
        return mAccExt.getFwVersion().getFullApplicationVersion();
    }

    public String getFrameworkBootloaderVersion() throws Exception {
        return mAccExt.getFwVersion().getBootloaderVersion();
    }

    public String getConnectionAddress() {
        return hAcTr.getAddress();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case NurDeviceListActivity.REQUEST_SELECT_DEVICE: {
                if (data == null || resultCode != NurDeviceListActivity.RESULT_OK)
                    return;

                try {
                    NurDeviceSpec spec = new NurDeviceSpec(data.getStringExtra(NurDeviceListActivity.SPECSTR));

                    if (hAcTr != null) {
                        System.out.println("Dispose transport");
                        hAcTr.dispose();
                    }

                    String strAddress;
                    hAcTr = NurDeviceSpec.createAutoConnectTransport(context, mNurApi, spec);
                    strAddress = spec.getAddress();
                    Log.i(TAG, "Dev selected: code = " + strAddress);
                    hAcTr.setAddress(strAddress);

                    showConnecting();

                    //If you want connect to same device automatically later on, you can save 'strAddress" and use that for connecting at app startup for example.
                    //saveSettings(spec);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            break;
        }
    }

}
