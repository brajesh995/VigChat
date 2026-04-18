package com.example.vigchat.adapters;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vigchat.R;
import com.example.vigchat.models.Message;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final List<Message> messages;
    private final String currentUserId;
    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

    public MessageAdapter(List<Message> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message msg = messages.get(position);
        boolean isCurrentUser = currentUserId != null && currentUserId.equals(msg.getSenderId());

        String displayName = msg.getSenderName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = isCurrentUser ? "You" : "Participant";
        }
        
        holder.senderText.setText(displayName);
        holder.metaText.setText(timeFormat.format(new Date(msg.getTimestamp())));

        if ("voice".equals(msg.getType())) {
            holder.text.setText("Voice message");
        } else if ("file".equals(msg.getType())) {
            holder.text.setText(buildAttachmentLabel(msg));
        } else {
            holder.text.setText(msg.getText());
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.messageBubble.getLayoutParams();
        params.gravity = isCurrentUser ? Gravity.END : Gravity.START;
        holder.messageBubble.setLayoutParams(params);
        holder.messageBubble.setBackgroundResource(
                isCurrentUser ? R.drawable.bg_message_self : R.drawable.bg_message_other
        );

        holder.itemView.setOnClickListener(v -> {
            if (!"text".equals(msg.getType()) && !TextUtils.isEmpty(msg.getFileUrl())) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.parse(msg.getFileUrl());
                String mimeType = msg.getMimeType();
                if (!TextUtils.isEmpty(mimeType)) {
                    intent.setDataAndType(uri, mimeType);
                } else {
                    intent.setData(uri);
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    v.getContext().startActivity(Intent.createChooser(intent, "Open attachment"));
                } catch (ActivityNotFoundException exception) {
                    Toast.makeText(v.getContext(), "No app found to open this attachment.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    private String buildAttachmentLabel(Message message) {
        String fileName = message.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "Attachment";
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return "[PDF] " + fileName;
        }
        if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) {
            return "[DOC] " + fileName;
        }
        if (lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx")) {
            return "[PPT] " + fileName;
        }
        if (lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a")) {
            return "[AUDIO] " + fileName;
        }
        if (lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".mov")) {
            return "[VIDEO] " + fileName;
        }
        return "[FILE] " + fileName;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout messageBubble;
        public TextView senderText;
        public TextView text;
        public TextView metaText;

        public ViewHolder(View itemView) {
            super(itemView);
            messageBubble = itemView.findViewById(R.id.messageBubble);
            senderText = itemView.findViewById(R.id.senderText);
            text = itemView.findViewById(R.id.messageText);
            metaText = itemView.findViewById(R.id.metaText);
        }
    }
}
