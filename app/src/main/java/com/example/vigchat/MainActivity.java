package com.example.vigchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.vigchat.activities.ChatActivity;
import com.example.vigchat.activities.CreateRoomActivity;
import com.example.vigchat.activities.QRScannerActivity;
import com.example.vigchat.utils.QRCodeHelper;

public class MainActivity extends AppCompatActivity {

    private EditText joinLinkInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        joinLinkInput = findViewById(R.id.joinLinkInput);
        findViewById(R.id.btnCreateRoom).setOnClickListener(v ->
                startActivity(new Intent(this, CreateRoomActivity.class))
        );

        findViewById(R.id.btnJoinRoom).setOnClickListener(v ->
                startActivity(new Intent(this, QRScannerActivity.class))
        );

        findViewById(R.id.btnJoinFromLink).setOnClickListener(v -> joinRoomFromInput());

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void joinRoomFromInput() {
        String input = joinLinkInput.getText().toString().trim();
        if (input.isEmpty()) {
            joinLinkInput.setError("Please enter a link or ID");
            return;
        }
        
        String roomId = QRCodeHelper.extractRoomId(input);
        if (roomId == null) {
            joinLinkInput.setError("Invalid room link or ID.");
            return;
        }
        joinLinkInput.setError(null);
        openChatRoom(roomId);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        
        String roomId = null;
        if (intent.getDataString() != null) {
            roomId = QRCodeHelper.extractRoomId(intent.getDataString());
        } else if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            roomId = QRCodeHelper.extractRoomId(sharedText);
        }

        if (roomId != null) {
            openChatRoom(roomId);
        }
    }

    private void openChatRoom(String roomId) {
        Intent chatIntent = new Intent(this, ChatActivity.class);
        chatIntent.putExtra("ROOM_ID", roomId);
        startActivity(chatIntent);
    }
}
