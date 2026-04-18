package com.example.vigchat.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.vigchat.models.ChatRoom;
import com.example.vigchat.models.Message;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LocalChatRepository {

    public interface MessagesObserver {
        void onMessagesChanged(@NonNull List<Message> messages);
    }

    public interface Subscription {
        void unsubscribe();
    }

    private static final String PREFS_NAME = "vigchat_local_store";
    private static final String KEY_CURRENT_USER_ID = "current_user_id";
    private static final String KEY_ROOMS = "rooms";
    private static final String KEY_MESSAGES_PREFIX = "messages_";

    private static final Map<String, List<MessagesObserver>> OBSERVERS = new HashMap<>();

    private LocalChatRepository() {
    }

    @NonNull
    public static synchronized String getOrCreateCurrentUserId(@NonNull Context context) {
        SharedPreferences preferences = prefs(context);
        String userId = preferences.getString(KEY_CURRENT_USER_ID, null);
        if (userId == null || userId.trim().isEmpty()) {
            userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
            preferences.edit().putString(KEY_CURRENT_USER_ID, userId).apply();
        }
        return userId;
    }

    public static synchronized void saveRoom(@NonNull Context context, @NonNull ChatRoom room) {
        JSONArray rooms = getRoomsArray(context);
        JSONArray updatedRooms = new JSONArray();
        boolean replaced = false;

        for (int i = 0; i < rooms.length(); i++) {
            JSONObject roomJson = rooms.optJSONObject(i);
            if (roomJson == null) {
                continue;
            }
            if (room.getRoomId().equals(roomJson.optString("roomId"))) {
                updatedRooms.put(toJson(room));
                replaced = true;
            } else {
                updatedRooms.put(roomJson);
            }
        }

        if (!replaced) {
            updatedRooms.put(toJson(room));
        }

        prefs(context).edit().putString(KEY_ROOMS, updatedRooms.toString()).apply();
    }

    @Nullable
    public static synchronized ChatRoom getRoom(@NonNull Context context, @NonNull String roomId) {
        JSONArray rooms = getRoomsArray(context);
        for (int i = 0; i < rooms.length(); i++) {
            JSONObject roomJson = rooms.optJSONObject(i);
            if (roomJson == null) {
                continue;
            }
            if (roomId.equals(roomJson.optString("roomId"))) {
                return fromRoomJson(roomJson);
            }
        }
        return null;
    }

    @NonNull
    public static synchronized List<Message> getMessages(@NonNull Context context, @NonNull String roomId) {
        JSONArray messages = getMessagesArray(context, roomId);
        List<Message> messageList = new ArrayList<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject messageJson = messages.optJSONObject(i);
            if (messageJson == null) {
                continue;
            }
            messageList.add(fromMessageJson(messageJson));
        }
        return messageList;
    }

    public static synchronized void addMessage(
            @NonNull Context context,
            @NonNull String roomId,
            @NonNull Message message
    ) {
        JSONArray messages = getMessagesArray(context, roomId);
        messages.put(toJson(message));
        prefs(context).edit().putString(KEY_MESSAGES_PREFIX + roomId, messages.toString()).apply();
        notifyMessagesChanged(context, roomId);
    }

    public static synchronized void deleteRoom(@NonNull Context context, @NonNull String roomId) {
        List<Message> existingMessages = getMessages(context, roomId);
        deleteLocalFiles(existingMessages);

        JSONArray rooms = getRoomsArray(context);
        JSONArray updatedRooms = new JSONArray();

        for (int i = 0; i < rooms.length(); i++) {
            JSONObject roomJson = rooms.optJSONObject(i);
            if (roomJson == null) {
                continue;
            }
            if (!roomId.equals(roomJson.optString("roomId"))) {
                updatedRooms.put(roomJson);
            }
        }

        prefs(context).edit()
                .putString(KEY_ROOMS, updatedRooms.toString())
                .remove(KEY_MESSAGES_PREFIX + roomId)
                .apply();

        notifyMessagesChanged(context, roomId);
    }

    @NonNull
    public static Subscription observeMessages(
            @NonNull Context context,
            @NonNull String roomId,
            @NonNull MessagesObserver observer
    ) {
        synchronized (LocalChatRepository.class) {
            List<MessagesObserver> roomObservers = OBSERVERS.get(roomId);
            if (roomObservers == null) {
                roomObservers = new ArrayList<>();
                OBSERVERS.put(roomId, roomObservers);
            }
            roomObservers.add(observer);
        }

        observer.onMessagesChanged(getMessages(context, roomId));

        return () -> {
            synchronized (LocalChatRepository.class) {
                List<MessagesObserver> roomObservers = OBSERVERS.get(roomId);
                if (roomObservers == null) {
                    return;
                }
                roomObservers.remove(observer);
                if (roomObservers.isEmpty()) {
                    OBSERVERS.remove(roomId);
                }
            }
        };
    }

    private static void notifyMessagesChanged(@NonNull Context context, @NonNull String roomId) {
        List<MessagesObserver> snapshot;
        synchronized (LocalChatRepository.class) {
            List<MessagesObserver> observers = OBSERVERS.get(roomId);
            if (observers == null || observers.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(observers);
        }

        List<Message> latestMessages = getMessages(context, roomId);
        for (MessagesObserver observer : snapshot) {
            observer.onMessagesChanged(latestMessages);
        }
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static JSONArray getRoomsArray(@NonNull Context context) {
        return parseArray(prefs(context).getString(KEY_ROOMS, "[]"));
    }

    @NonNull
    private static JSONArray getMessagesArray(@NonNull Context context, @NonNull String roomId) {
        return parseArray(prefs(context).getString(KEY_MESSAGES_PREFIX + roomId, "[]"));
    }

    @NonNull
    private static JSONArray parseArray(@Nullable String rawJson) {
        try {
            return new JSONArray(rawJson == null ? "[]" : rawJson);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    @NonNull
    private static JSONObject toJson(@NonNull ChatRoom room) {
        JSONObject json = new JSONObject();
        try {
            json.put("roomId", room.getRoomId());
            json.put("adminId", room.getAdminId());
            json.put("joinLink", room.getJoinLink());
            json.put("createdAt", room.getCreatedAt());
        } catch (Exception ignored) {
        }
        return json;
    }

    @NonNull
    private static ChatRoom fromRoomJson(@NonNull JSONObject json) {
        return new ChatRoom(
                json.optString("roomId"),
                json.optString("adminId"),
                json.optString("joinLink"),
                json.optLong("createdAt")
        );
    }

    @NonNull
    private static JSONObject toJson(@NonNull Message message) {
        JSONObject json = new JSONObject();
        try {
            json.put("senderId", message.getSenderId());
            json.put("senderName", message.getSenderName());
            json.put("text", message.getText());
            json.put("fileUrl", message.getFileUrl());
            json.put("fileName", message.getFileName());
            json.put("mimeType", message.getMimeType());
            json.put("storagePath", message.getStoragePath());
            json.put("type", message.getType());
            json.put("timestamp", message.getTimestamp());
        } catch (Exception ignored) {
        }
        return json;
    }

    @NonNull
    private static Message fromMessageJson(@NonNull JSONObject json) {
        return new Message(
                json.optString("senderId"),
                json.optString("senderName"),
                json.optString("text"),
                nullableString(json.optString("fileUrl", null)),
                nullableString(json.optString("fileName", null)),
                nullableString(json.optString("mimeType", null)),
                nullableString(json.optString("storagePath", null)),
                json.optString("type"),
                json.optLong("timestamp")
        );
    }

    @Nullable
    private static String nullableString(@Nullable String value) {
        if (value == null || "null".equals(value)) {
            return null;
        }
        return value;
    }

    private static void deleteLocalFiles(@NonNull List<Message> messages) {
        for (Message message : messages) {
            if (message.getStoragePath() == null || message.getStoragePath().trim().isEmpty()) {
                continue;
            }

            String storagePath = message.getStoragePath();
            if (storagePath.startsWith("/")) {
                File file = new File(storagePath);
                if (file.exists()) {
                    file.delete();
                }
            } else {
                try {
                    Uri uri = Uri.parse(storagePath);
                    if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
                        String path = uri.getPath();
                        if (path != null) {
                            File file = new File(path);
                            if (file.exists()) {
                                file.delete();
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
