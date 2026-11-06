package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.interpret.InterpretationException;
import com.sssok.renewal.application.organization.interpret.InterpretationException.Reason;
import com.sssok.renewal.application.organization.interpret.OrganizeInterpretation;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class OpenAiOrganizeRequestInterpreterTest {
    private final List<OrganizeTool> tools = List.of(
        new OrganizeTool("gallery", "group_photos_by_person", "사람별로 묶는다"),
        new OrganizeTool("gallery", "group_similar_photos", "유사 사진을 묶는다"));
    private final ChatModel model = mock(ChatModel.class);
    private final OpenAiOrganizeRequestInterpreter interpreter =
        new OpenAiOrganizeRequestInterpreter(model, new ObjectMapper());

    @Test
    void 목록에_새_도구를_추가하면_코드_분기_없이_선택한다() {
        OrganizeTool added = new OrganizeTool("gallery", "group_by_scene", "장면별로 묶는다");
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls", call("analysis_0", "{}")));
        assertThat(interpreter.interpret("장면별로 모아줘", List.of(added)))
            .isEqualTo(new OrganizeInterpretation.ToolSelected(added));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void 전달한_목록의_도구를_선택한다(int index) {
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls", call("analysis_" + index, "{}")));
        assertThat(interpreter.interpret("사진 정리", tools))
            .isEqualTo(new OrganizeInterpretation.ToolSelected(tools.get(index)));
    }

    @Test
    void 같은_이름의_도구도_서버를_구분한다() {
        OrganizeTool another = new OrganizeTool("other", tools.getFirst().name(), "다른 분석");
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls", call("analysis_1", "{}")));
        assertThat(interpreter.interpret("다른 분석", List.of(tools.getFirst(), another)))
            .isEqualTo(new OrganizeInterpretation.ToolSelected(another));
    }

    @Test
    void 명확화와_미지원은_도구_선택과_구분한다() {
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls", call("clarify", "{}")));
        assertThat(interpreter.interpret("정리해줘", tools)).isInstanceOf(OrganizeInterpretation.NeedsClarification.class);
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls", call("unsupported", "{}")));
        assertThat(interpreter.interpret("삭제해줘", tools)).isInstanceOf(OrganizeInterpretation.Unsupported.class);
    }

    @Test
    void 도구가_없으면_모델을_호출하지_않는다() {
        assertThatThrownBy(() -> interpreter.interpret("정리해줘", List.of()))
            .isInstanceOfSatisfying(InterpretationException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.CATALOG_UNAVAILABLE));
        verifyNoInteractions(model);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void 빈_요청은_외부_호출_전에_거절한다(String text) {
        assertThatThrownBy(() -> interpreter.interpret(text, tools)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(model);
    }

    @Test
    void 길이_제한을_넘으면_외부_호출을_하지_않는다() {
        assertThatThrownBy(() -> interpreter.interpret("가".repeat(1001), tools))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{", "{} {}", "{\"mediaId\":42}"})
    void 빈_객체가_아닌_인자는_거절한다(String arguments) {
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls", call("analysis_0", arguments)));
        assertInvalidResponse();
    }

    @Test
    void 미등록_도구는_거절한다() {
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls", call("delete_photos", "{}")));
        assertInvalidResponse();
    }

    @Test
    void 복수_도구_선택은_거절한다() {
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls",
            call("analysis_0", "{}"), call("analysis_1", "{}")));
        assertInvalidResponse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"length", "content_filter", "stop"})
    void 정상_도구_선택으로_종료하지_않으면_거절한다(String finishReason) {
        when(model.call(any(Prompt.class))).thenReturn(response(finishReason, call("analysis_0", "{}")));
        assertInvalidResponse();
    }

    @Test
    void 도구_선택이_없는_응답은_거절한다() {
        when(model.call(any(Prompt.class))).thenReturn(response("tool_calls"));
        assertInvalidResponse();
    }

    @Test
    void 응답이_없으면_거절한다() {
        when(model.call(any(Prompt.class))).thenReturn(null);
        assertInvalidResponse();
    }

    @Test
    void 호출_실패는_미지원_요청과_구분하고_제공자_본문을_노출하지_않는다() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("private provider body"));
        assertThatThrownBy(() -> interpreter.interpret("사람별로 모아줘", tools))
            .isInstanceOfSatisfying(InterpretationException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.CALL_FAILED))
            .hasMessageNotContaining("private provider body")
            .hasNoCause();
    }

    private void assertInvalidResponse() {
        assertThatThrownBy(() -> interpreter.interpret("사람별로 모아줘", tools))
            .isInstanceOfSatisfying(InterpretationException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_RESPONSE));
    }

    private ToolCall call(String name, String arguments) {
        return new ToolCall("selection", "function", name, arguments);
    }

    private ChatResponse response(String finishReason, ToolCall... calls) {
        AssistantMessage message = AssistantMessage.builder().content("").toolCalls(List.of(calls)).build();
        return new ChatResponse(List.of(new Generation(message,
            ChatGenerationMetadata.builder().finishReason(finishReason).build())));
    }
}
