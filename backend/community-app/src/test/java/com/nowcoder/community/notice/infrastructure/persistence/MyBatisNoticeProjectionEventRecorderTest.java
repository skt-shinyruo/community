package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.notice.infrastructure.persistence.mapper.NoticeProjectionEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MyBatisNoticeProjectionEventRecorderTest {

    @Test
    void tryRecordShouldReturnFalseForDuplicateInsert() {
        NoticeProjectionEventMapper mapper = mock(NoticeProjectionEventMapper.class);
        when(mapper.insert("evt-duplicate")).thenThrow(new DuplicateKeyException("duplicate"));
        MyBatisNoticeProjectionEventRecorder recorder = new MyBatisNoticeProjectionEventRecorder(mapper);

        boolean recorded = recorder.tryRecord("evt-duplicate");

        assertThat(recorded).isFalse();
        verify(mapper).insert("evt-duplicate");
    }

    @Test
    void tryRecordShouldPropagateNonDuplicateIntegrityFailures() {
        NoticeProjectionEventMapper mapper = mock(NoticeProjectionEventMapper.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("not-null violation");
        when(mapper.insert("evt-invalid")).thenThrow(failure);
        MyBatisNoticeProjectionEventRecorder recorder = new MyBatisNoticeProjectionEventRecorder(mapper);

        assertThatThrownBy(() -> recorder.tryRecord("evt-invalid"))
                .isSameAs(failure);

        verify(mapper).insert("evt-invalid");
    }

    @Test
    void tryRecordShouldReturnFalseForBlankIdWithoutMapperCall() {
        NoticeProjectionEventMapper mapper = mock(NoticeProjectionEventMapper.class);
        MyBatisNoticeProjectionEventRecorder recorder = new MyBatisNoticeProjectionEventRecorder(mapper);

        boolean recorded = recorder.tryRecord("  ");

        assertThat(recorded).isFalse();
        verifyNoInteractions(mapper);
    }

    @Test
    void tryRecordShouldTrimIdBeforeInsert() {
        NoticeProjectionEventMapper mapper = mock(NoticeProjectionEventMapper.class);
        when(mapper.insert("evt-trimmed")).thenReturn(1);
        MyBatisNoticeProjectionEventRecorder recorder = new MyBatisNoticeProjectionEventRecorder(mapper);

        boolean recorded = recorder.tryRecord("  evt-trimmed  ");

        assertThat(recorded).isTrue();
        verify(mapper).insert("evt-trimmed");
        verify(mapper, never()).insert("  evt-trimmed  ");
    }

    @Test
    void tryRecordShouldReturnTrueForSuccessfulInsert() {
        NoticeProjectionEventMapper mapper = mock(NoticeProjectionEventMapper.class);
        when(mapper.insert("evt-success")).thenReturn(1);
        MyBatisNoticeProjectionEventRecorder recorder = new MyBatisNoticeProjectionEventRecorder(mapper);

        boolean recorded = recorder.tryRecord("evt-success");

        assertThat(recorded).isTrue();
        verify(mapper).insert("evt-success");
    }
}
