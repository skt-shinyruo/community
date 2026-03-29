// 治理服务：处理举报审核与处置动作（隐藏/删除/警告/禁言/封禁），并记录审计与通知。
package com.nowcoder.community.content.service;

import com.nowcoder.community.content.event.payload.CommentPayload;
import com.nowcoder.community.content.event.payload.ModerationCommandPayload;
import com.nowcoder.community.content.event.payload.ModerationPayload;
import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ReportResponse;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.mapper.ModerationActionMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class ModerationService {

    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_HIDE = "hide";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_WARN = "warn";
    public static final String ACTION_MUTE = "mute";
    public static final String ACTION_BAN = "ban";

    private static final int DEFAULT_MUTE_SECONDS = 24 * 3600;
    private static final int DEFAULT_BAN_SECONDS = 7 * 24 * 3600;

    private final ReportService reportService;
    private final ModerationActionMapper actionMapper;
    private final DiscussPostMapper discussPostMapper;
    private final CommentMapper commentMapper;
    private final ContentEventPublisher eventPublisher;

    public ModerationService(
            ReportService reportService,
            ModerationActionMapper actionMapper,
            DiscussPostMapper discussPostMapper,
            CommentMapper commentMapper,
            ContentEventPublisher eventPublisher
    ) {
        this.reportService = reportService;
        this.actionMapper = actionMapper;
        this.discussPostMapper = discussPostMapper;
        this.commentMapper = commentMapper;
        this.eventPublisher = eventPublisher;
    }

    public List<Report> listReports(Integer status, Integer targetType, Integer reporterId, int page, int size) {
        return reportService.listReports(status, targetType, reporterId, page, size);
    }

    public List<ReportResponse> listReportResponses(Integer status, Integer targetType, Integer reporterId, int page, int size) {
        return listReports(status, targetType, reporterId, page, size).stream()
                .map(this::toReportResponse)
                .toList();
    }

    public List<ModerationAction> listActions(Integer actorId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Integer aid = actorId == null ? null : actorId;
        if (aid != null && aid <= 0) {
            aid = null;
        }
        return actionMapper.selectActions(aid, Pagination.safeOffset(p, s), s);
    }

    public List<ModerationActionResponse> listModerationActionResponses(Integer actorId, int page, int size) {
        return listActions(actorId, page, size).stream()
                .map(this::toModerationActionResponse)
                .toList();
    }

    @Transactional
    public int takeAction(int actorId, int reportId, String action, String reason, Integer durationSeconds) {
        if (actorId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorId 非法");
        }
        if (reportId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "reportId 非法");
        }
        String act = safeLower(action);
        if (!isSupportedAction(act)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 非法");
        }
        String rsn = safeTrim(reason);
        if (!StringUtils.hasText(rsn)) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }

        Report report = reportService.getById(reportId);
        if (report.getStatus() != ReportService.STATUS_PENDING) {
            throw new BusinessException(INVALID_ARGUMENT, "该举报已处理");
        }

        // 处置记录（审计）
        ModerationAction row = new ModerationAction();
        row.setReportId(reportId);
        row.setActorId(actorId);
        row.setAction(act);
        row.setReason(rsn);
        row.setDurationSeconds(durationSeconds == null ? 0 : Math.max(0, durationSeconds));
        row.setCreateTime(new Date());
        actionMapper.insertAction(row);

        // 主处置逻辑（按 targetType 解析目标用户与目标对象）
        Target target = resolveTarget(report);

        if (ACTION_REJECT.equals(act)) {
            reportService.markStatus(reportId, ReportService.STATUS_REJECTED);
            publishNotices(report, row, target, "to_reporter", report.getReporterId());
            return row.getId();
        }

        if (ACTION_HIDE.equals(act) || ACTION_DELETE.equals(act)) {
            applyContentAction(actorId, target, act, rsn);
            reportService.markStatus(reportId, ReportService.STATUS_PROCESSED);
            publishNotices(report, row, target, "to_target", target.targetUserId);
            publishNotices(report, row, target, "to_reporter", report.getReporterId());
            return row.getId();
        }

        if (ACTION_WARN.equals(act)) {
            // 警告：只做通知与审计
            reportService.markStatus(reportId, ReportService.STATUS_PROCESSED);
            publishNotices(report, row, target, "to_target", target.targetUserId);
            publishNotices(report, row, target, "to_reporter", report.getReporterId());
            return row.getId();
        }

        if (ACTION_MUTE.equals(act)) {
            int seconds = durationSeconds == null || durationSeconds <= 0 ? DEFAULT_MUTE_SECONDS : durationSeconds;
            publishModerationCommand(actorId, reportId, target.targetUserId, ACTION_MUTE, seconds, rsn);
            reportService.markStatus(reportId, ReportService.STATUS_PROCESSED);
            row.setDurationSeconds(seconds);
            publishNotices(report, row, target, "to_target", target.targetUserId);
            publishNotices(report, row, target, "to_reporter", report.getReporterId());
            return row.getId();
        }

        if (ACTION_BAN.equals(act)) {
            int seconds = durationSeconds == null || durationSeconds <= 0 ? DEFAULT_BAN_SECONDS : durationSeconds;
            publishModerationCommand(actorId, reportId, target.targetUserId, ACTION_BAN, seconds, rsn);
            reportService.markStatus(reportId, ReportService.STATUS_PROCESSED);
            row.setDurationSeconds(seconds);
            publishNotices(report, row, target, "to_target", target.targetUserId);
            publishNotices(report, row, target, "to_reporter", report.getReporterId());
            return row.getId();
        }

        throw new BusinessException(INVALID_ARGUMENT, "未支持的 action");
    }

    private void applyContentAction(int actorId, Target target, String action, String reason) {
        if (target.targetType == ReportService.TARGET_TYPE_POST) {
            int updated = discussPostMapper.updateModerationDeleteMeta(
                    target.targetId,
                    2,
                    actorId,
                    buildDeletedReason(action, reason),
                    new Date()
            );
            if (updated <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "帖子状态更新失败");
            }
            publishPostDeletedEvent(target.targetId);
            return;
        }

        if (target.targetType == ReportService.TARGET_TYPE_COMMENT) {
            int updated = commentMapper.updateModerationDeleteMeta(
                    target.targetId,
                    1,
                    actorId,
                    buildDeletedReason(action, reason),
                    new Date()
            );
            if (updated <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "评论状态更新失败");
            }
            publishCommentDeletedEvent(target.targetId);
            return;
        }

        // user 类型不支持 hide/delete
        throw new BusinessException(FORBIDDEN, "该目标类型不支持此处置动作");
    }

    private void publishPostDeletedEvent(int postId) {
        if (postId <= 0) {
            return;
        }
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getId() <= 0) {
            return;
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(post.getId());
        payload.setUserId(post.getUserId());
        payload.setCategoryId(post.getCategoryId());
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(Instant.now());
        payload.setScore(post.getScore());
        eventPublisher.publishPostDeleted(payload);
    }

    private void publishCommentDeletedEvent(int commentId) {
        if (commentId <= 0) {
            return;
        }
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getId() <= 0) {
            return;
        }
        int postId = resolveRootPostIdByComment(comment, 12);
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(comment.getId());
        payload.setPostId(Math.max(0, postId));
        payload.setUserId(comment.getUserId());
        payload.setEntityType(comment.getEntityType());
        payload.setEntityId(comment.getEntityId());
        payload.setCreateTime(Instant.now());
        eventPublisher.publishCommentDeleted(payload);
    }

    private int resolveRootPostIdByComment(Comment comment, int maxHops) {
        if (comment == null || comment.getId() <= 0) {
            return 0;
        }
        int t = comment.getEntityType();
        int id = comment.getEntityId();
        for (int i = 0; i < Math.max(1, maxHops); i++) {
            if (t == ReportService.TARGET_TYPE_POST) {
                return id;
            }
            if (t != ReportService.TARGET_TYPE_COMMENT || id <= 0) {
                return 0;
            }
            Comment parent = commentMapper.selectCommentById(id);
            if (parent == null || parent.getId() <= 0 || parent.getStatus() != 0) {
                return 0;
            }
            t = parent.getEntityType();
            id = parent.getEntityId();
        }
        return 0;
    }

    private void publishNotices(Report report, ModerationAction action, Target target, String kind, int toUserId) {
        if (toUserId <= 0) {
            return;
        }

        ModerationPayload payload = new ModerationPayload();
        payload.setReportId(report == null ? null : report.getId());
        payload.setKind(kind);
        payload.setToUserId(toUserId);
        payload.setActorUserId(action == null ? null : action.getActorId());
        payload.setTargetType(target == null ? null : target.targetType);
        payload.setTargetId(target == null ? null : target.targetId);
        payload.setAction(action == null ? null : action.getAction());
        payload.setReason(action == null ? null : action.getReason());
        payload.setDurationSeconds(action == null ? null : action.getDurationSeconds());
        payload.setCreateTime(Instant.now());
        eventPublisher.publishModerationActionApplied(payload);
    }

    private void publishModerationCommand(int actorUserId, int reportId, int targetUserId, String action, int durationSeconds, String reason) {
        if (targetUserId <= 0) {
            return;
        }
        ModerationCommandPayload cmd = new ModerationCommandPayload();
        cmd.setUserId(targetUserId);
        cmd.setAction(action);
        cmd.setDurationSeconds(Math.max(0, durationSeconds));
        cmd.setActorUserId(actorUserId <= 0 ? null : actorUserId);
        cmd.setReportId(reportId <= 0 ? null : reportId);
        cmd.setReason(reason);
        eventPublisher.publishModerationCommandRequested(cmd);
    }

    private Target resolveTarget(Report report) {
        int type = report.getTargetType();
        int targetId = report.getTargetId();

        if (type == ReportService.TARGET_TYPE_POST) {
            DiscussPost post = discussPostMapper.selectDiscussPostById(targetId);
            if (post == null || post.getStatus() == 2) {
                throw new BusinessException(POST_NOT_FOUND);
            }
            return new Target(type, targetId, post.getUserId());
        }

        if (type == ReportService.TARGET_TYPE_COMMENT) {
            Comment c = commentMapper.selectCommentById(targetId);
            if (c == null || c.getStatus() != 0) {
                throw new BusinessException(COMMENT_NOT_FOUND);
            }
            return new Target(type, targetId, c.getUserId());
        }

        if (type == ReportService.TARGET_TYPE_USER) {
            return new Target(type, targetId, targetId);
        }

        throw new BusinessException(INVALID_ARGUMENT, "targetType 非法");
    }

    private boolean isSupportedAction(String act) {
        return ACTION_REJECT.equals(act)
                || ACTION_HIDE.equals(act)
                || ACTION_DELETE.equals(act)
                || ACTION_WARN.equals(act)
                || ACTION_MUTE.equals(act)
                || ACTION_BAN.equals(act);
    }

    private ReportResponse toReportResponse(Report report) {
        ReportResponse response = new ReportResponse();
        response.setId(report.getId());
        response.setReporterId(report.getReporterId());
        response.setTargetType(report.getTargetType());
        response.setTargetId(report.getTargetId());
        response.setReason(report.getReason());
        response.setDetail(report.getDetail());
        response.setStatus(report.getStatus());
        response.setCreateTime(report.getCreateTime());
        return response;
    }

    private ModerationActionResponse toModerationActionResponse(ModerationAction action) {
        ModerationActionResponse response = new ModerationActionResponse();
        response.setId(action.getId());
        response.setReportId(action.getReportId());
        response.setActorId(action.getActorId());
        response.setAction(action.getAction());
        response.setReason(action.getReason());
        response.setDurationSeconds(action.getDurationSeconds());
        response.setCreateTime(action.getCreateTime());
        return response;
    }

    private String buildDeletedReason(String action, String reason) {
        String a = safeLower(action);
        String r = safeTrim(reason);
        if (r.isEmpty()) {
            return a;
        }
        // 保持可读性，避免把敏感细节写入公开字段（公开页面不展示，但仍尽量克制）。
        if (r.length() > 180) {
            r = r.substring(0, 180);
        }
        return a + ": " + r;
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeLower(String s) {
        return safeTrim(s).toLowerCase();
    }

    private record Target(int targetType, int targetId, int targetUserId) {
    }
}
