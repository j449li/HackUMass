package com.mobility42.azurechatr.util;

import android.bluetooth.BluetoothSocket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by alqiluo on 2015-10-24.
 */
public class ServerSocket {

    public ServerSocket() { }

    public static void sendData(BluetoothSocket socket, String data) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(data.getBytes());
    }

    public static String receiveData(BluetoothSocket socket) throws IOException{
        byte[] buffer = new byte[6000];
        ByteArrayInputStream input = new ByteArrayInputStream(buffer);
        InputStream inputStream = socket.getInputStream();
        inputStream.read(buffer);
        String data = "";
        for(byte b : buffer) {
            data += (char) b;
        }
        return data;
    }
}
