package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.pagination.Pagination;
import com.nowcoder.community.notice.application.result.NoticeItemResult;
import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.domain.model.NoticeTopic;
import com.nowcoder.community.notice.domain.model.NoticeTopicSummary;
import com.nowcoder.community.notice.domain.repository.NoticeRepository;
import com.nowcoder.community.notice.domain.service.NoticeDomainService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.UUID;

@Service
public class NoticeApplicationService {

    public static final UUID SYSTEM_NOTICE_SENDER_ID = NoticeRecord.SYSTEM_NOTICE_SENDER_ID;
    public static final int STATUS_UNREAD = NoticeDomainService.STATUS_UNREAD;
    public static final int STATUS_READ = NoticeDomainService.STATUS_READ;
    public static final int STATUS_REVOKED = 2;

    private final NoticeRepository noticeRepository;
    private final NoticeDomainService noticeDomainService = new NoticeDomainService();
    private final UuidV7Generator idGenerator;
    private final Clock clock;

    public NoticeApplicationService(
            NoticeRepository noticeRepository,
            UuidV7Generator idGenerator,
            Clock clock
    ) {
        this.noticeRepository = Objects.requireNonNull(noticeRepository, "noticeRepository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void createNotice(CreateNoticeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        noticeDomainService.validateCreate(command.toUserId(), command.noticeTopic(), command.contentJson());
        NoticeRecord notice = new NoticeRecord();
        notice.setId(idGenerator.next());
        notice.setSenderUserId(SYSTEM_NOTICE_SENDER_ID);
        notice.setRecipientUserId(command.toUserId());
        notice.setTopic(command.noticeTopic());
        notice.setContent(command.contentJson());
        notice.setSourceEventType(command.sourceEventType());
        notice.setSourceRelationKey(command.sourceRelationKey());
        notice.setStatus(STATUS_UNREAD);
        notice.setCreateTime(Date.from(clock.instant()));
        noticeRepository.insert(notice);
    }

    public List<NoticeRecord> listNotices(UUID userId, String noticeTopic, int page, int size) {
        int p = noticeDomainService.pageOrDefault(page);
        int s = noticeDomainService.sizeOrDefault(size);
        int offset = Pagination.safeOffset(p, s);
        return noticeRepository.findByUserAndTopic(userId, noticeTopic, offset, s);
    }

    public List<NoticeItemResult> listNoticeItems(ListNoticeItemsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        int p = noticeDomainService.pageOrDefault(command.page());
        int s = noticeDomainService.sizeOrDefault(command.size());
        int offset = Pagination.safeOffset(p, s);
        List<NoticeRecord> list = noticeRepository.findByUserAndTopic(command.userId(), command.noticeTopic(), offset, s);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(this::toNoticeItemResult).toList();
    }

    public List<NoticeItemResult> listNoticeItems(UUID userId, String noticeTopic, Integer page, Integer size) {
        return listNoticeItems(new ListNoticeItemsCommand(userId, noticeTopic, page, size));
    }

    public int unreadCount(UUID userId, String noticeTopic) {
        return noticeRepository.unreadCount(userId, noticeTopic);
    }

    public List<NoticeTopicSummaryResult> topicSummary(UUID userId) {
        Map<String, NoticeTopicSummary> summaries = noticeRepository
                .summarizeByUserAndTopics(userId, NoticeTopic.DEFAULT_TOPICS)
                .stream()
                .filter(summary -> summary != null
                        && summary.latest() != null
                        && summary.latest().getTopic() != null)
                .collect(Collectors.toMap(
                        summary -> summary.latest().getTopic(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
        return NoticeTopic.DEFAULT_TOPICS.stream()
                .map(topic -> toTopicSummaryResult(topic, summaries.get(topic)))
                .toList();
    }

    private NoticeTopicSummaryResult toTopicSummaryResult(String topic, NoticeTopicSummary summary) {
        if (summary == null) {
            return new NoticeTopicSummaryResult(topic, null, 0, 0);
        }
        return new NoticeTopicSummaryResult(
                topic,
                toNoticeItemResult(summary.latest()),
                summary.noticeCount(),
                summary.unreadCount()
        );
    }

    public void markRead(UUID userId, List<UUID> ids) {
        List<UUID> normalizedIds = noticeDomainService.normalizeMarkReadIds(ids);
        if (normalizedIds.isEmpty()) {
            return;
        }
        noticeRepository.markUnreadAsRead(userId, normalizedIds);
    }

    public void revokeLikeNotice(UUID recipientUserId, String relationKey) {
        if (recipientUserId == null || relationKey == null || relationKey.isBlank()) {
            return;
        }
        noticeRepository.revokeLikeNotice(recipientUserId, relationKey.trim(), STATUS_REVOKED);
    }

    private NoticeItemResult toNoticeItemResult(NoticeRecord notice) {
        return new NoticeItemResult(
                notice.getId(),
                notice.getSenderUserId(),
                notice.getRecipientUserId(),
                notice.getTopic(),
                notice.getContent(),
                notice.getStatus(),
                notice.getCreateTime()
        );
    }

    public record CreateNoticeCommand(
            UUID toUserId,
            String noticeTopic,
            String contentJson,
            String sourceEventType,
            String sourceRelationKey
    ) {
    }

    public record ListNoticeItemsCommand(
            UUID userId,
            String noticeTopic,
            Integer page,
            Integer size
    ) {
    }

    public record NoticeTopicSummaryResult(
            String noticeTopic,
            NoticeItemResult latest,
            int noticeCount,
            int unreadCount
    ) {
    }
}
