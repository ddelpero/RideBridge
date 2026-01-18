package com.ddelpero.ridebridge.notifications;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationData {
    public String appPackage;
    public String appName;
    public String sender;
    public String message;
    public long timestamp;
    
    public NotificationData(String appPackage, String appName, String sender, String message) {
        this.appPackage = appPackage;
        this.appName = appName;
        this.sender = sender;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", "notification");
        json.put("appPackage", appPackage);
        json.put("appName", appName);
        json.put("sender", sender);
        json.put("message", message);
        json.put("timestamp", timestamp);
        return json;
    }
    
    public static NotificationData fromJson(JSONObject json) throws JSONException {
        NotificationData data = new NotificationData(
            json.getString("appPackage"),
            json.getString("appName"),
            json.getString("sender"),
            json.getString("message")
        );
        data.timestamp = json.getLong("timestamp");
        return data;
    }
}
