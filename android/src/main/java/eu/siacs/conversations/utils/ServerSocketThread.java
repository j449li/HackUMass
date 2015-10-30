package eu.siacs.conversations.utils;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import eu.siacs.conversations.services.BlueToothService;
import eu.siacs.conversations.ui.ConversationActivity;

/**
 * Created by alqiluo on 2015-10-24.
 */
public class ServerSocketThread extends Thread {
    private BluetoothSocket bTSocket;
    BluetoothServerSocket serverSocket;

    private BlueToothService service;

    public ServerSocketThread(BlueToothService service) { this.service = service; }

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
                String data = "";
                try {
                    bTSocket = serverSocket.accept();
                } catch (IOException e) {
                    Log.d("SERVERCONNECT", "Could not accept an incoming connection.");
                    break;
                }
                if (bTSocket != null) {
                    try {
                        data += (char) ServerSocket.receiveData(bTSocket);
                    } catch (IOException e) {
                        Log.d("SERVERCONNECT", "Could not close ServerSocket:" + e.toString());
                    }
                } else {
                    RequestPackage requestPackage = new RequestPackage(data);

                    service.handleRequestPackage(requestPackage);

                    data = "";
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
