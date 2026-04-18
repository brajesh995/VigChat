package com.example.vigchat.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vigchat.R;
import com.example.vigchat.adapters.MessageAdapter;
import com.example.vigchat.models.Message;
import com.example.vigchat.utils.FileUploadHelper;
import com.example.vigchat.utils.FirebaseHelper;
import com.example.vigchat.utils.QRCodeHelper;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private String roomId;
    private String currentUserId;
    private String currentUserName;
    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    private final List<Message> messageList = new ArrayList<>();
    private EditText messageInput;
    private Button sendBtn;
    private Button fileBtn;
    private Button voiceBtn;
    private Button deleteBtn;
    private TextView roomInfoText;

    private MediaRecorder recorder;
    private String filePath;
    private boolean isAdmin = false;
    private ListenerRegistration messageSubscription;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    attachFile(uri);
                }
            });

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecording();
                } else {
                    Toast.makeText(this, "Microphone permission is only needed when you record voice.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        roomId = QRCodeHelper.extractRoomId(getIntent().getStringExtra("ROOM_ID"));
        if (roomId == null && getIntent().getDataString() != null) {
            roomId = QRCodeHelper.extractRoomId(getIntent().getDataString());
        }

        if (roomId == null) {
            Toast.makeText(this, "Invalid room", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initUI();
        setupRecyclerView();
        setComposerEnabled(false);
        
        showNameInputDialog();
    }

    private void showNameInputDialog() {
        EditText nameInput = new EditText(this);
        nameInput.setHint("Your Name");
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Enter your name")
                .setMessage("Choose a name to be seen in the chat.")
                .setView(nameInput)
                .setCancelable(false)
                .setPositiveButton("Join", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (!name.isEmpty()) {
                        currentUserName = name;
                        signInAndLoadRoom();
                    } else {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        showNameInputDialog();
                    }
                })
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }

    private void signInAndLoadRoom() {
        FirebaseHelper.ensureSignedIn(
                userId -> {
                    currentUserId = userId;
                    adapter = new MessageAdapter(messageList, currentUserId);
                    recyclerView.setAdapter(adapter);
                    FirebaseHelper.joinMember(roomId, currentUserId);
                    loadRoom();
                },
                exception -> {
                    Toast.makeText(
                            this,
                            FirebaseHelper.toUserMessage(exception, "joining room"),
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                }
        );
    }

    private void initUI() {
        roomInfoText = findViewById(R.id.roomInfoText);
        recyclerView = findViewById(R.id.recyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendBtn = findViewById(R.id.sendBtn);
        fileBtn = findViewById(R.id.fileBtn);
        voiceBtn = findViewById(R.id.voiceBtn);
        deleteBtn = findViewById(R.id.deleteBtn);

        deleteBtn.setVisibility(View.GONE);
        roomInfoText.setText("Room ID: " + roomId);

        sendBtn.setOnClickListener(v -> sendMessage());
        fileBtn.setOnClickListener(v -> pickFile());
        voiceBtn.setOnClickListener(v -> toggleRecording());
        deleteBtn.setOnClickListener(v -> {
            if (isAdmin) {
                confirmDeleteRoom();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(messageList, "");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadRoom() {
        FirebaseHelper.loadRoom(
                roomId,
                room -> {
                    isAdmin = currentUserId.equals(room.getAdminId());
                    deleteBtn.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                    roomInfoText.setText(isAdmin
                            ? "Room ID: " + roomId + "  |  Admin"
                            : "Room ID: " + roomId + "  |  Participant");
                    setComposerEnabled(true);
                    observeMessages();
                },
                exception -> {
                    Toast.makeText(
                            this,
                            FirebaseHelper.toUserMessage(exception, "loading room"),
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                }
        );
    }

    private void observeMessages() {
        if (messageSubscription != null) {
            messageSubscription.remove();
        }

        messageSubscription = FirebaseHelper.observeMessages(
                roomId,
                messages -> {
                    messageList.clear();
                    messageList.addAll(messages);
                    adapter.notifyDataSetChanged();
                    if (!messageList.isEmpty()) {
                        recyclerView.scrollToPosition(messageList.size() - 1);
                    }
                },
                exception -> Toast.makeText(
                        this,
                        FirebaseHelper.toUserMessage(exception, "loading messages"),
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        setComposerEnabled(false);
        Message msg = new Message(
                currentUserId,
                currentUserName,
                text,
                null,
                null,
                null,
                null,
                "text",
                System.currentTimeMillis()
        );

        sendMessage(msg, "sending message", () -> {
            messageInput.setText("");
            setComposerEnabled(true);
        });
    }

    private void confirmDeleteRoom() {
        new AlertDialog.Builder(this)
                .setTitle("Delete this room?")
                .setMessage("This removes the room, its messages, and uploaded attachments for everyone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteRoom())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRoom() {
        deleteBtn.setEnabled(false);
        FirebaseHelper.deleteRoomAndContents(
                roomId,
                () -> {
                    Toast.makeText(this, "Room deleted", Toast.LENGTH_SHORT).show();
                    finish();
                },
                exception -> {
                    deleteBtn.setEnabled(true);
                    Toast.makeText(
                            this,
                            FirebaseHelper.toUserMessage(exception, "deleting room"),
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

    private void toggleRecording() {
        if (recorder == null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        File cacheDirectory = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
        filePath = new File(cacheDirectory, "voice_" + System.currentTimeMillis() + ".3gp").getAbsolutePath();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            recorder = new MediaRecorder(this);
        } else {
            recorder = new MediaRecorder();
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(filePath);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
            recorder.start();
            voiceBtn.setText(getString(R.string.stop_recording));
        } catch (Exception e) {
            cleanupRecorder(false);
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (recorder == null) {
            return;
        }

        try {
            recorder.stop();
        } catch (Exception e) {
            cleanupRecorder(false);
            Toast.makeText(this, "Unable to finish the recording.", Toast.LENGTH_SHORT).show();
            return;
        }

        cleanupRecorder(true);
        addVoiceMessage();
    }

    private void addVoiceMessage() {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }

        setComposerEnabled(false);
        FileUploadHelper.uploadVoiceRecording(
                filePath,
                roomId,
                new FileUploadHelper.UploadCallback() {
                    @Override
                    public void onSuccess(@NonNull FileUploadHelper.UploadedFile uploadedFile) {
                        Message msg = new Message(
                                currentUserId,
                                currentUserName,
                                "",
                                uploadedFile.getDownloadUrl(),
                                uploadedFile.getFileName(),
                                uploadedFile.getMimeType(),
                                uploadedFile.getStoragePath(),
                                "voice",
                                System.currentTimeMillis()
                        );
                        sendMessage(msg, "sending voice message", () -> {
                            setComposerEnabled(true);
                            deleteLocalRecording();
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        setComposerEnabled(true);
                        Toast.makeText(
                                ChatActivity.this,
                                FirebaseHelper.toUserMessage(exception, "uploading voice message"),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void pickFile() {
        filePickerLauncher.launch(new String[]{"*/*"});
    }

    private void attachFile(Uri fileUri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    fileUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
        }

        setComposerEnabled(false);
        FileUploadHelper.uploadFile(
                this,
                fileUri,
                roomId,
                new FileUploadHelper.UploadCallback() {
                    @Override
                    public void onSuccess(@NonNull FileUploadHelper.UploadedFile uploadedFile) {
                        Message msg = new Message(
                                currentUserId,
                                currentUserName,
                                "",
                                uploadedFile.getDownloadUrl(),
                                uploadedFile.getFileName(),
                                uploadedFile.getMimeType(),
                                uploadedFile.getStoragePath(),
                                "file",
                                System.currentTimeMillis()
                        );
                        sendMessage(msg, "sending attachment", () -> setComposerEnabled(true));
                    }

                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        setComposerEnabled(true);
                        Toast.makeText(
                                ChatActivity.this,
                                FirebaseHelper.toUserMessage(exception, "uploading attachment"),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void setComposerEnabled(boolean enabled) {
        messageInput.setEnabled(enabled);
        sendBtn.setEnabled(enabled);
        fileBtn.setEnabled(enabled);
        voiceBtn.setEnabled(enabled);
    }

    private void cleanupRecorder(boolean keepFile) {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        voiceBtn.setText(getString(R.string.voice));
        if (!keepFile) {
            deleteLocalRecording();
        }
    }

    private void deleteLocalRecording() {
        if (!TextUtils.isEmpty(filePath)) {
            File localFile = new File(filePath);
            if (localFile.exists()) {
                localFile.delete();
            }
        }
        filePath = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (recorder != null) {
            cleanupRecorder(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageSubscription != null) {
            messageSubscription.remove();
            messageSubscription = null;
        }
        if (roomId != null && currentUserId != null) {
            FirebaseHelper.leaveMember(roomId, currentUserId);
        }
    }

    private void sendMessage(
            @NonNull Message message,
            @NonNull String action,
            @NonNull Runnable onSuccess
    ) {
        FirebaseHelper.sendMessage(
                roomId,
                message,
                onSuccess::run,
                exception -> {
                    setComposerEnabled(true);
                    Toast.makeText(
                            this,
                            FirebaseHelper.toUserMessage(exception, action),
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }
}
