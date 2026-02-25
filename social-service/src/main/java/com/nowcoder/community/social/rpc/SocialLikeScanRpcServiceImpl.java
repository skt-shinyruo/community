package com.nowcoder.community.social.rpc;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.social.api.rpc.SocialLikeScanRpcService;
import com.nowcoder.community.social.api.rpc.dto.SocialLikeScanResponse;
import com.nowcoder.community.social.like.LikeMapper;
import com.nowcoder.community.social.like.LikeScanRow;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@DubboService
public class SocialLikeScanRpcServiceImpl implements SocialLikeScanRpcService {

    private final LikeMapper likeMapper;

    public SocialLikeScanRpcServiceImpl(LikeMapper likeMapper) {
        this.likeMapper = likeMapper;
    }

    @Override
    public Result<SocialLikeScanResponse> scan(int entityType, long afterEntityId, long afterUserId, int limit) {
        try {
            if (!EntityTypes.isValid(entityType)) {
                throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
            }
            long ae = Math.max(0L, afterEntityId);
            long au = Math.max(0L, afterUserId);
            int l = limit <= 0 ? 1000 : Math.min(2000, Math.max(1, limit));

            List<LikeScanRow> rows = likeMapper.scanLikes(entityType, ae, au, l);
            SocialLikeScanResponse resp = new SocialLikeScanResponse();

            List<SocialLikeScanResponse.SocialLikeScanItem> items = rows == null
                    ? List.of()
                    : rows.stream().map(r -> {
                        SocialLikeScanResponse.SocialLikeScanItem i = new SocialLikeScanResponse.SocialLikeScanItem();
                        if (r != null) {
                            i.setEntityId(r.getEntityId());
                            i.setUserId(r.getUserId());
                        }
                        return i;
                    }).toList();
            resp.setItems(items);

            if (rows != null && !rows.isEmpty()) {
                LikeScanRow last = rows.get(rows.size() - 1);
                if (last != null) {
                    resp.setNextAfterEntityId(last.getEntityId());
                    resp.setNextAfterUserId(last.getUserId());
                }
            }
            resp.setHasMore(rows != null && rows.size() >= l);
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();
        return Result.error(ec.getCode(), msg);
    }
}

