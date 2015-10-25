package eu.siacs.conversations.services;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.bluetooth.BluetoothAdapter;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.EnableBlueToothActivity;
import eu.siacs.conversations.utils.RequestPackage;
import eu.siacs.conversations.utils.ServerSocket;
import eu.siacs.conversations.utils.ServerSocketThread;

/**
 * Created by alqiluo on 2015-10-24.
 */
public class BlueToothService extends Service {

    public static final String REQUEST_PACKAGE = "request_package";
    public static final String HANDLE_REQUEST_PACKAGE = "handle_request_package";
    BluetoothAdapter bluetoothAdapter;
    Set<BluetoothDevice> pairedDevices;
    IntentFilter filter;
    BroadcastReceiver receiver;
    ServerSocketThread serverSocketThread = new ServerSocketThread(this);
    static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    Map<String, Set<Long>> processedRequests = new HashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter==null){
            Toast.makeText(getApplicationContext(), "No bluetooth detected", 0).show();
            ((Activity) getApplicationContext()).finish();
        }
        else{
            registerBroadcastReceivers();

            if(!bluetoothAdapter.isEnabled()){
                turnOnBlueTooth();
            } else {
                updatePairedDevices();
            }
        }
    }

    private void updatePairedDevices() {
        if(serverSocketThread != null)
            serverSocketThread.closeConnect();

        pairedDevices = bluetoothAdapter.getBondedDevices();
        bluetoothAdapter.startDiscovery();

        serverSocketThread.acceptConnect(bluetoothAdapter, uuid);
        serverSocketThread.start();
    }

    private void registerBroadcastReceivers() {
        filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        receiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (BluetoothDevice.ACTION_FOUND.equals(action)){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    pairDevice(device);
                }
                else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    switch (state) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            if(serverSocketThread != null)
                                serverSocketThread.closeConnect();
                            sendBroadcast(new Intent(ConversationActivity.CLOSE_APP));
                            break;
                        case BluetoothAdapter.STATE_ON:
                            updatePairedDevices();
                    }
                }
                else if(HANDLE_REQUEST_PACKAGE.equals(action)) {
                    RequestPackage requestPackage = (RequestPackage) intent.getExtras().get(REQUEST_PACKAGE);
                    handleRequestPackage(requestPackage);
                }

            }
        };

        registerReceiver(receiver, filter);
    }

    private void turnOnBlueTooth() {
        Intent dialogIntent = new Intent(this, EnableBlueToothActivity.class);
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(dialogIntent);
    }

    private void startDiscovery() {
        bluetoothAdapter.cancelDiscovery();
        bluetoothAdapter.startDiscovery();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
    }

    //For Pairing
    private void pairDevice(BluetoothDevice device) {
        try {
            Log.d("pairDevice()", "Start Pairing...");
            device.getClass().getMethod("setPairingConfirmation", boolean.class).invoke(device, true);
            device.getClass().getMethod("cancelPairingUserInput").invoke(device);
            device.getClass().getMethod("createBond", (Class[]) null).invoke(device, (Object[]) null);
            Log.d("pairDevice()", "Pairing finished.");
        } catch (Exception e) {
            Log.e("pairDevice()", e.getMessage());
        }
    }


    //For UnPairing
    private void unpairDevice(BluetoothDevice device) {
        try {
            Log.d("unpairDevice()", "Start Un-Pairing...");
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            Log.d("unpairDevice()", "Un-Pairing finished.");
        } catch (Exception e) {
            Log.e("unpairDevice()", e.getMessage());
        }
    }

    private void dataRequest(final RequestPackage requestPackage) {
        requestPackage.relayPath.add("Jays Arebad");

        String url = "https://bluchain.azure-mobile.net/api/estar";
        AsyncHttpClient client = new AsyncHttpClient();
        try{
            client.post(getApplicationContext(), url, new StringEntity(requestPackage.toString()), "application/json", new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                try {
                    RequestPackage responsePackage;
                    JSONObject obj = response.getJSONObject(0);
                    responsePackage = (RequestPackage) obj.get("response");
                    relayRequest(responsePackage);
                } catch (Exception e) {

                }
                }
            });
        } catch (Exception e) {

        }
    }

    private void relayRequest(RequestPackage responsePackage) {
        if(processedRequests.containsKey(responsePackage.originalSenderId) && processedRequests.get(responsePackage.originalSenderId).contains(responsePackage.requestId)) {
            return;
        }

        for(BluetoothDevice pairedDevice: pairedDevices) {
            BluetoothSocket socket = connect(pairedDevice, uuid);
            if(socket != null) {
                try {
                    ServerSocket.sendData(socket, responsePackage.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void handleRequestPackage(RequestPackage requestPackage) {
        if(isNetworkAvailable()) {
            dataRequest(requestPackage);
        } else {
            relayRequest(requestPackage);
        }
    }

    public BluetoothSocket connect(BluetoothDevice bTDevice, UUID mUUID) {
        BluetoothSocket bTSocket = null;
        try {
            bTSocket = bTDevice.createRfcommSocketToServiceRecord(mUUID);
        } catch (IOException e) {
            Log.d("CONNECTTHREAD","Could not create RFCOMM socket:" + e.toString());
            return null;
        }
        try {
            bTSocket.connect();
        } catch(IOException e) {
            Log.d("CONNECTTHREAD","Could not connect: " + e.toString());
            try {
                bTSocket.close();
            } catch(IOException close) {
                Log.d("CONNECTTHREAD", "Could not close connection:" + e.toString());
                return null;
            }
        }
        return bTSocket;
    }
}
