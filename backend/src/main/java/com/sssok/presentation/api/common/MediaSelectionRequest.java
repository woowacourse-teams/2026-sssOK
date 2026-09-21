package com.sssok.presentation.api.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sssok.application.media.MediaSelection;
import com.sssok.application.media.MediaSelectionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "미디어 선택 범위. include는 ids만, exclude는 방 전체에서 ids를 제외해 선택한다")
public record MediaSelectionRequest(
    @Schema(description = "선택 범위 해석 방식", allowableValues = {"include", "exclude"}, example = "include")
    Mode mode,
    @Schema(description = "포함하거나 제외할 미디어 ID 목록", example = "[1, 3, 5]")
    List<Long> ids
) {

    public MediaSelection toSelection() {
        return new MediaSelection(mode == null ? null : mode.applicationMode(), ids);
    }

    public enum Mode {
        INCLUDE,
        EXCLUDE;

        @JsonCreator
        public static Mode from(String value) {
            if (value == null) {
                return null;
            }
            return switch (value) {
                case "include" -> INCLUDE;
                case "exclude" -> EXCLUDE;
                default -> throw new IllegalArgumentException("지원하지 않는 선택 모드입니다: " + value);
            };
        }

        @JsonValue
        public String value() {
            return name().toLowerCase();
        }

        private MediaSelectionMode applicationMode() {
            return MediaSelectionMode.valueOf(name());
        }
    }
}
