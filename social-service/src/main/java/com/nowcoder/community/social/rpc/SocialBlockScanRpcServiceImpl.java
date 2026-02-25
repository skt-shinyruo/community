package com.nowcoder.community.social.rpc;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.social.api.rpc.SocialBlockScanRpcService;
import com.nowcoder.community.social.api.rpc.dto.SocialBlockScanResponse;
import com.nowcoder.community.social.block.BlockMapper;
import com.nowcoder.community.social.block.BlockScanRow;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.util.List;

@DubboService
public class SocialBlockScanRpcServiceImpl implements SocialBlockScanRpcService {

    private final BlockMapper blockMapper;

    public SocialBlockScanRpcServiceImpl(BlockMapper blockMapper) {
        this.blockMapper = blockMapper;
    }

    @Override
    public Result<SocialBlockScanResponse> scan(int afterBlockerUserId, int afterBlockedUserId, int limit) {
        try {
            int au = Math.max(0, afterBlockerUserId);
            int at = Math.max(0, afterBlockedUserId);
            int l = limit <= 0 ? 1000 : Math.min(2000, Math.max(1, limit));

            List<BlockScanRow> rows = blockMapper.scanBlocks(au, at, l);
            SocialBlockScanResponse resp = new SocialBlockScanResponse();

            List<SocialBlockScanResponse.SocialBlockScanItem> items = rows == null
                    ? List.of()
                    : rows.stream().map(r -> {
                        SocialBlockScanResponse.SocialBlockScanItem i = new SocialBlockScanResponse.SocialBlockScanItem();
                        if (r != null) {
                            i.setBlockerUserId(r.getUserId());
                            i.setBlockedUserId(r.getTargetUserId());
                        }
                        return i;
                    }).toList();
            resp.setItems(items);

            if (rows != null && !rows.isEmpty()) {
                BlockScanRow last = rows.get(rows.size() - 1);
                if (last != null) {
                    resp.setNextAfterBlockerUserId(last.getUserId());
                    resp.setNextAfterBlockedUserId(last.getTargetUserId());
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

