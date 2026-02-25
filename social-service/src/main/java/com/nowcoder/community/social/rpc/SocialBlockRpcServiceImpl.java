package com.nowcoder.community.social.rpc;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.social.api.rpc.SocialBlockRpcService;
import com.nowcoder.community.social.block.BlockService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

@DubboService
public class SocialBlockRpcServiceImpl implements SocialBlockRpcService {

    private final BlockService blockService;

    public SocialBlockRpcServiceImpl(BlockService blockService) {
        this.blockService = blockService;
    }

    @Override
    public Result<Boolean> isEitherBlocked(int userIdA, int userIdB) {
        try {
            if (userIdA <= 0 || userIdB <= 0 || userIdA == userIdB) {
                return Result.ok(false);
            }
            return Result.ok(blockService.isEitherBlocked(userIdA, userIdB));
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

