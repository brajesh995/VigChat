package com.example.vigchat.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vigchat.R;
import com.example.vigchat.utils.FirebaseHelper;
import com.example.vigchat.utils.QRCodeHelper;

import java.util.UUID;

public class CreateRoomActivity extends AppCompatActivity {

    private Button createRoomBtn;
    private Button scanBtn;
    private Button copyLinkBtn;
    private Button shareLinkBtn;
    private ImageView qrImage;
    private TextView roomStatusText;
    private TextView roomLinkText;
    private String currentRoomId;
    private String currentRoomLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        createRoomBtn = findViewById(R.id.createRoomBtn);
        scanBtn = findViewById(R.id.scanBtn);
        copyLinkBtn = findViewById(R.id.copyLinkBtn);
        shareLinkBtn = findViewById(R.id.shareLinkBtn);
        qrImage = findViewById(R.id.qrImage);
        roomStatusText = findViewById(R.id.roomStatusText);
        roomLinkText = findViewById(R.id.roomLinkText);

        createRoomBtn.setOnClickListener(v -> {
            if (currentRoomId == null) {
                signInAndCreateRoom();
            } else {
                goToChat();
            }
        });

        scanBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, QRScannerActivity.class));
            finish();
        });

        copyLinkBtn.setOnClickListener(v -> copyRoomLink());
        shareLinkBtn.setOnClickListener(v -> shareRoomLink());
    }

    private void signInAndCreateRoom() {
        createRoomBtn.setEnabled(false);
        roomStatusText.setText("Signing in to create your room...");
        FirebaseHelper.ensureSignedIn(
                this::createRoom,
                exception -> {
                    createRoomBtn.setEnabled(true);
                    roomStatusText.setText("Unable to create room");
                    Toast.makeText(
                            this,
                            FirebaseHelper.toUserMessage(exception, "creating room"),
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

    private void createRoom(String userId) {
        createRoomBtn.setEnabled(false);
        String roomId = UUID.randomUUID().toString();
        String joinLink = QRCodeHelper.generateRoomLink(roomId);
        long createdAt = System.currentTimeMillis();

        roomStatusText.setText("Creating room...");
        FirebaseHelper.createRoom(
                roomId,
                userId,
                joinLink,
                createdAt,
                room -> {
                    currentRoomId = room.getRoomId();
                    currentRoomLink = room.getJoinLink();

                    Bitmap qr = QRCodeHelper.generateQR(currentRoomLink);
                    if (qr != null) {
                        qrImage.setImageBitmap(qr);
                    }

                    roomStatusText.setText("Room ready. Share this QR code or link to join from other devices.");
                    roomLinkText.setText(currentRoomLink);
                    roomLinkText.setVisibility(View.VISIBLE);
                    copyLinkBtn.setVisibility(View.VISIBLE);
                    shareLinkBtn.setVisibility(View.VISIBLE);
                    createRoomBtn.setText(R.string.enter_chat_room);
                    createRoomBtn.setEnabled(true);
                    Toast.makeText(this, "Room created successfully", Toast.LENGTH_SHORT).show();
                },
                exception -> {
                    createRoomBtn.setEnabled(true);
                    roomStatusText.setText("Unable to create room");
                    Toast.makeText(
                            this,
                            FirebaseHelper.toUserMessage(exception, "creating room"),
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

    private void goToChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("ROOM_ID", currentRoomId);
        startActivity(intent);
        finish();
    }

    private void copyRoomLink() {
        if (currentRoomLink == null) {
            return;
        }

        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("VigChat room link", currentRoomLink));
            Toast.makeText(this, "Room link copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareRoomLink() {
        if (currentRoomLink == null) {
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Join my VigChat room");
        shareIntent.putExtra(Intent.EXTRA_TEXT, currentRoomLink);
        startActivity(Intent.createChooser(shareIntent, "Share room link"));
    }
}
