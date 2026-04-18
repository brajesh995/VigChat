package com.example.vigchat.utils;

import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class QRCodeHelper {

    private static final String HTTPS_LINK_PREFIX = "https://vigchat.app/chat/";
    private static final String APP_LINK_PREFIX = "vigchat://chat/";

    private QRCodeHelper() {
    }

    public static Bitmap generateQR(String text) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            return encoder.encodeBitmap(text, BarcodeFormat.QR_CODE, 400, 400);
        } catch (Exception e) {
            return null;
        }
    }

    public static String generateRoomLink(String roomId) {
        return HTTPS_LINK_PREFIX + roomId;
    }

    @Nullable
    public static String extractRoomId(@Nullable String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String candidate = rawInput.trim();
        if (candidate.isEmpty()) {
            return null;
        }

        // Check for direct ID first if it matches the pattern
        if (candidate.matches("[A-Za-z0-9\\-]{8,}") && !candidate.contains("/") && !candidate.contains(":")) {
            return candidate;
        }

        try {
            Uri uri = Uri.parse(candidate);
            if (uri != null && uri.getScheme() != null) {
                // Handle deep links like vigchat://chat/ROOM_ID
                if ("vigchat".equalsIgnoreCase(uri.getScheme()) && "chat".equalsIgnoreCase(uri.getHost())) {
                    String path = uri.getPath();
                    if (path != null && path.length() > 1) {
                        return sanitizeRoomId(path.substring(1));
                    }
                }

                // Handle HTTPS links like https://vigchat.app/chat/ROOM_ID
                if (uri.getHost() != null && uri.getHost().contains("vigchat.app")) {
                    if (uri.getPathSegments() != null && uri.getPathSegments().size() >= 2) {
                        if ("chat".equalsIgnoreCase(uri.getPathSegments().get(0))) {
                            return sanitizeRoomId(uri.getPathSegments().get(1));
                        }
                    }
                }

                // Fallback for roomId query parameter
                String roomIdParam = uri.getQueryParameter("roomId");
                if (roomIdParam != null) {
                    return sanitizeRoomId(roomIdParam);
                }
            }
        } catch (Exception ignored) {
        }

        // Last ditch effort: find the last path segment manually
        if (candidate.contains("/")) {
            String potentialId = candidate.substring(candidate.lastIndexOf('/') + 1);
            if (potentialId.contains("?")) {
                potentialId = potentialId.substring(0, potentialId.indexOf('?'));
            }
            if (potentialId.contains("#")) {
                potentialId = potentialId.substring(0, potentialId.indexOf('#'));
            }
            return sanitizeRoomId(potentialId);
        }

        return sanitizeRoomId(candidate);
    }

    @Nullable
    private static String sanitizeRoomId(@Nullable String roomId) {
        if (TextUtils.isEmpty(roomId)) {
            return null;
        }

        String trimmed = roomId.trim();
        // Room IDs are UUIDs or similar alphanumeric strings with dashes, at least 8 chars
        return trimmed.matches("[A-Za-z0-9\\-]{8,}") ? trimmed : null;
    }
}
