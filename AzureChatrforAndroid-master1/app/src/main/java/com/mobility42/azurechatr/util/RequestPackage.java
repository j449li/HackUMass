package com.mobility42.azurechatr.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alqiluo on 2015-10-24.
 */
public class RequestPackage implements Serializable {
    public String senderId;
    public Long requestId;
    public String destinationId;
    public String request;
    public List<String> relayPath = new ArrayList<>();

    public RequestPackage(String str) {
        String[] split = str.split("|");
        senderId = split[0];
        requestId = Long.valueOf(split[1]);
        destinationId = split[2];
        request = split[3];
        for(int i = 4; i < split.length; i++) {
            relayPath.add(split[i]);
        }
    }

    @Override
    public String toString(){
        String str = "<" + senderId + "|" + requestId + "|" + destinationId + "|" + request;
        for(String relay : relayPath) {
            str += "|" + relay;
        }
        str += ">";
        return str;
    }

}
