package com.mobility42.azurechatr.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.mobility42.azurechatr.ChatActivity;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by alqiluo on 2015-10-24.
 */
public class ServerSocketThread extends Thread {
    private BluetoothSocket bTSocket;
    BluetoothServerSocket serverSocket;

    private ChatActivity activity;

    public ServerSocketThread(ChatActivity activity) { this.activity = activity; }

    public void acceptConnect(BluetoothAdapter bTAdapter, UUID mUUID) {
        try {
            serverSocket = bTAdapter.listenUsingRfcommWithServiceRecord("Service_Name", mUUID);
        } catch(IOException e) {
            Log.d("SERVERCONNECT", "Could not get a BluetoothServerSocket:" + e.toString());
        }
    }

    @Override
    public void run() {
        try {
            while(true) {
                sleep(1000);
                try {
                    bTSocket = serverSocket.accept();
                } catch (IOException e) {
                    Log.d("SERVERCONNECT", "Could not accept an incoming connection.");
                    break;
                }
                if (bTSocket != null) {
                    try {
                        String data = ServerSocket.receiveData(bTSocket);
                        RequestPackage requestPackage = new RequestPackage(data);
                        activity.handleRequestPackage(requestPackage);
                    } catch (IOException e) {
                        Log.d("SERVERCONNECT", "Could not close ServerSocket:" + e.toString());
                    }
                } else {
                    break;
                }
            }
        } catch (InterruptedException e) {

        }
    }

    public void closeConnect() {
        if(bTSocket != null)
        try {
            bTSocket.close();
        } catch(IOException e) {
            Log.d("SERVERCONNECT", "Could not close connection:" + e.toString());
        }
    }
}
