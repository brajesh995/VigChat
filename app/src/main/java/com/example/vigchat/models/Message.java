package com.example.vigchat.models;

import androidx.annotation.Keep;

@Keep
public class Message {
    private String senderId;
    private String senderName;
    private String text;
    private String fileUrl;
    private String fileName;
    private String mimeType;
    private String storagePath;
    private String type;
    private long timestamp;

    public Message() {}

    public Message(
            String senderId,
            String senderName,
            String text,
            String fileUrl,
            String fileName,
            String mimeType,
            String storagePath,
            String type,
            long timestamp
    ) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.storagePath = storagePath;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
