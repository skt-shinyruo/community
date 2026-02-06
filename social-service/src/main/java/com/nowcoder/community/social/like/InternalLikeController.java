package com.nowcoder.community.social.like;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

/**
 * social-service 内部 likes 扫描接口：供下游（content-service）回填 Redis 点赞投影使用。
 *
 * <p>安全：开发阶段内部接口默认放行；生产建议通过网络隔离/网关策略收敛暴露面，并避免对外暴露 /internal/**。</p>
 */
@RestController
@RequestMapping("/internal/social/likes")
public class InternalLikeController {

    private final LikeMapper likeMapper;

    public InternalLikeController(LikeMapper likeMapper) {
        this.likeMapper = likeMapper;
    }

    @GetMapping("/scan")
    public Result<LikeScanResponse> scan(
            @RequestParam int entityType,
            @RequestParam(required = false) Long afterEntityId,
            @RequestParam(required = false) Long afterUserId,
            @RequestParam(required = false) Integer limit
    ) {
        if (!EntityTypes.isValid(entityType)) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        long ae = afterEntityId == null ? 0L : Math.max(0L, afterEntityId);
        long au = afterUserId == null ? 0L : Math.max(0L, afterUserId);
        int l = limit == null ? 1000 : Math.min(2000, Math.max(1, limit));

        List<LikeScanRow> rows = likeMapper.scanLikes(entityType, ae, au, l);
        LikeScanResponse resp = new LikeScanResponse();
        resp.setItems(rows == null ? List.of() : rows.stream().map(LikeScanItem::fromRow).toList());

        if (rows != null && !rows.isEmpty()) {
            LikeScanRow last = rows.get(rows.size() - 1);
            resp.setNextAfterEntityId(last.getEntityId());
            resp.setNextAfterUserId(last.getUserId());
        }
        resp.setHasMore(rows != null && rows.size() >= l);
        return Result.ok(resp);
    }

    public static class LikeScanResponse {
        private List<LikeScanItem> items;
        private boolean hasMore;
        private Long nextAfterEntityId;
        private Long nextAfterUserId;

        public List<LikeScanItem> getItems() {
            return items == null ? List.of() : items;
        }

        public void setItems(List<LikeScanItem> items) {
            this.items = items;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Long getNextAfterEntityId() {
            return nextAfterEntityId;
        }

        public void setNextAfterEntityId(Long nextAfterEntityId) {
            this.nextAfterEntityId = nextAfterEntityId;
        }

        public Long getNextAfterUserId() {
            return nextAfterUserId;
        }

        public void setNextAfterUserId(Long nextAfterUserId) {
            this.nextAfterUserId = nextAfterUserId;
        }
    }

    public static class LikeScanItem {
        private long entityId;
        private long userId;

        public static LikeScanItem fromRow(LikeScanRow r) {
            LikeScanItem i = new LikeScanItem();
            if (r != null) {
                i.entityId = r.getEntityId();
                i.userId = r.getUserId();
            }
            return i;
        }

        public long getEntityId() {
            return entityId;
        }

        public void setEntityId(long entityId) {
            this.entityId = entityId;
        }

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }
    }
}
