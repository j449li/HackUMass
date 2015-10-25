package eu.siacs.conversations.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alqiluo on 2015-10-24.
 */
public class RequestPackage implements Serializable {
    public Long originalSenderId;
    public Long requestId;
    public String request;
    public List<String> relayPath = new ArrayList<>();

    public RequestPackage(String str) {
        String[] split = str.split("|");
        originalSenderId = Long.valueOf(split[0]);
        requestId = Long.valueOf(split[1]);
        request = split[2];
        for(int i = 3; i < split.length; i++) {
            relayPath.add(split[i]);
        }
    }

    @Override
    public String toString(){
        String str = "<" + originalSenderId + "|" + requestId + "|" + request;
        for(String relay : relayPath) {
            str += "|" + relay;
        }
        str += ">";
        return str;
    }

}
