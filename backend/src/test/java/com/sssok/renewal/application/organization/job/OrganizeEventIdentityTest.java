package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizeEventIdentityTest {
    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "COMPLETED", "FAILED"})
    void 재시도_전_실행의_늦은_이벤트는_확인만_하고_현재_작업을_바꾸지_않는다(String state) {
        OrganizeJobRepository repository = mock(OrganizeJobRepository.class);
        OrganizeResultValidator validator = mock(OrganizeResultValidator.class);
        OrganizeChangeNotifier notifier = mock(OrganizeChangeNotifier.class);
        OrganizeJob job = mock(OrganizeJob.class);
        UUID jobId = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();
        OrganizeExecution old = new OrganizeExecution(10L, 1L, oldId, 1, OrganizeExecutionStatus.FAILED,
            1, null, null, null, null, null);
        OrganizeExecution current = new OrganizeExecution(11L, 1L, UUID.randomUUID(), 2,
            OrganizeExecutionStatus.DISPATCH_PENDING, 0, null, null, null, null, null);
        given(job.id()).willReturn(1L);
        given(job.publicId()).willReturn(jobId);
        given(job.roomId()).willReturn(2L);
        given(job.kind()).willReturn(OrganizeKind.PERSON);
        given(job.status()).willReturn(OrganizeJobStatus.QUEUED);
        given(repository.findExecutionByExecutionId(oldId)).willReturn(Optional.of(old));
        given(repository.findById(1L)).willReturn(Optional.of(job));
        given(repository.findLatestExecution(1L)).willReturn(Optional.of(current));
        OrganizeEventService service = new OrganizeEventService(repository, validator,
            mock(OrganizeJobTransitions.class), notifier, mock(OrganizeJobProperties.class), new ObjectMapper(), Clock.systemUTC());

        var ack = service.handle(new OrganizeEvent(jobId, 2L, oldId, 1, "PERSON", state, null, null, null));

        assertThat(ack).isEqualTo(new OrganizeEventAck(true, oldId, state));
        verify(repository).findExecutionByExecutionId(oldId);
        verify(repository).findById(1L);
        verify(repository).findLatestExecution(1L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(validator, notifier);
    }
}
