package com.sssok.renewal.application.media.upload.complete;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sssok.presentation.api.common.GlobalExceptionHandler;
import com.sssok.renewal.application.media.upload.exception.UploadVerificationUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class UploadVerificationHttpTest {

    @RestController
    static class FailureController {
        @GetMapping("/upload-verification-test")
        String fail() {
            throw new UploadVerificationUnavailableException();
        }
    }

    @Test
    void 업로드_확인이_지연되면_재시도_가능한_503을_반환한다() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailureController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mvc.perform(get("/upload-verification-test"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "1"))
            .andExpect(jsonPath("$.code").value("UPLOAD_VERIFICATION_UNAVAILABLE"));
    }
}
