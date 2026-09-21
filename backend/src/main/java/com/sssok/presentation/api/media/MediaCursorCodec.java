package com.sssok.presentation.api.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.media.MediaCursor;
import com.sssok.application.media.exception.InvalidCursorException;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaCursorCodec {

    private final ObjectMapper objectMapper;

    public String encode(MediaCursor cursor) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("미디어 커서를 인코딩할 수 없습니다", exception);
        }
    }

    public MediaCursor decode(String encodedCursor, Long roomId, Long folderId) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor);
            MediaCursor cursor = objectMapper.readValue(json, MediaCursor.class);
            requireValid(cursor, roomId, folderId);
            return cursor;
        } catch (IllegalArgumentException | IOException exception) {
            throw new InvalidCursorException();
        }
    }

    private void requireValid(MediaCursor cursor, Long roomId, Long folderId) {
        if (cursor == null
            || cursor.roomId() == null
            || cursor.lastCreatedAt() == null
            || cursor.lastMediaId() == null
            || cursor.lastMediaId() <= 0
            || !Objects.equals(cursor.roomId(), roomId)
            || !Objects.equals(cursor.folderId(), folderId)) {
            throw new InvalidCursorException();
        }
    }
}
