package com.sssok.renewal.domain.organization;

import static com.sssok.renewal.domain.organization.OrganizeJobStatus.APPLIED;
import static com.sssok.renewal.domain.organization.OrganizeJobStatus.CANCELED;
import static com.sssok.renewal.domain.organization.OrganizeJobStatus.EXPIRED;
import static com.sssok.renewal.domain.organization.OrganizeJobStatus.FAILED;
import static com.sssok.renewal.domain.organization.OrganizeJobStatus.QUEUED;
import static com.sssok.renewal.domain.organization.OrganizeJobStatus.READY;
import static com.sssok.renewal.domain.organization.OrganizeJobStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrganizeJobStatusTest {

    @Test
    void 분석_중인_상태는_QUEUED와_RUNNING뿐이다() {
        assertThat(QUEUED.isAnalyzing()).isTrue();
        assertThat(RUNNING.isAnalyzing()).isTrue();
        assertThat(READY.isAnalyzing()).isFalse();
        assertThat(CANCELED.isAnalyzing()).isFalse();
    }

    @Test
    void 방을_점유하는_활성_작업은_검토_중인_READY도_포함한다() {
        assertThat(QUEUED.holdsRoom()).isTrue();
        assertThat(RUNNING.holdsRoom()).isTrue();
        assertThat(READY.holdsRoom()).isTrue();
        assertThat(FAILED.holdsRoom()).isFalse();
        assertThat(EXPIRED.holdsRoom()).isFalse();
    }

    @Test
    void 종료_상태는_실패_취소_반영_만료이다() {
        assertThat(OrganizeJobStatus.terminal()).containsExactly(FAILED, CANCELED, APPLIED, EXPIRED);
    }

    @Test
    void 취소는_진행_중이거나_검토_중일_때만_가능하다() {
        assertThat(QUEUED.isCancelable()).isTrue();
        assertThat(RUNNING.isCancelable()).isTrue();
        assertThat(READY.isCancelable()).isTrue();
        assertThat(APPLIED.isCancelable()).isFalse();
        assertThat(FAILED.isCancelable()).isFalse();
    }
}
