package com.nowcoder.community.notice.application;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.pagination.Pagination;
import com.nowcoder.community.notice.application.command.CreateNoticeCommand;
import com.nowcoder.community.notice.application.command.ListNoticeItemsCommand;
import com.nowcoder.community.notice.application.command.MarkNoticeReadCommand;
import com.nowcoder.community.notice.application.result.NoticeItemResult;
import com.nowcoder.community.notice.application.result.NoticeTopicSummaryResult;
import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.domain.model.NoticeTopic;
import com.nowcoder.community.notice.domain.repository.NoticeRepository;
import com.nowcoder.community.notice.domain.service.NoticeDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NoticeApplicationService {

    public static final UUID SYSTEM_NOTICE_SENDER_ID = NoticeRecord.SYSTEM_NOTICE_SENDER_ID;
    public static final int STATUS_UNREAD = NoticeDomainService.STATUS_UNREAD;
    public static final int STATUS_READ = NoticeDomainService.STATUS_READ;
    public static final int STATUS_REVOKED = 2;

    private final NoticeRepository noticeRepository;
    private final NoticeDomainService noticeDomainService;
    private final UuidV7Generator idGenerator;

    @Autowired
    public NoticeApplicationService(NoticeRepository noticeRepository) {
        this(noticeRepository, new NoticeDomainService(), new UuidV7Generator());
    }

    NoticeApplicationService(
            NoticeRepository noticeRepository,
            NoticeDomainService noticeDomainService,
            UuidV7Generator idGenerator
    ) {
        this.noticeRepository = noticeRepository;
        this.noticeDomainService = noticeDomainService;
        this.idGenerator = idGenerator;
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
        notice.setCreateTime(new Date());
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
        return NoticeTopic.DEFAULT_TOPICS.stream().map(noticeTopic -> {
            List<NoticeRecord> latest = noticeRepository.findByUserAndTopic(userId, noticeTopic, 0, 1);
            return new NoticeTopicSummaryResult(
                    noticeTopic,
                    latest == null || latest.isEmpty() ? null : toNoticeItemResult(latest.get(0)),
                    noticeRepository.count(userId, noticeTopic),
                    noticeRepository.unreadCount(userId, noticeTopic)
            );
        }).toList();
    }

    public void markRead(MarkNoticeReadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.ids() == null || command.ids().isEmpty()) {
            return;
        }
        noticeRepository.markRead(command.userId(), command.ids(), STATUS_READ);
    }

    public void markRead(UUID userId, List<UUID> ids) {
        markRead(new MarkNoticeReadCommand(userId, ids));
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
}
