package com.nowcoder.community.user.rpc;

// user-service 的 Dubbo Provider：实现读取型 RPC（用户摘要/批量摘要/用户名解析）。
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.api.rpc.UserReadRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserSummary;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import com.nowcoder.community.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class UserReadRpcServiceImpl implements UserReadRpcService {

    private final UserService userService;
    private final InternalUserService internalUserService;

    public UserReadRpcServiceImpl(UserService userService, InternalUserService internalUserService) {
        this.userService = userService;
        this.internalUserService = internalUserService;
    }

    @Override
    public Result<UserSummary> resolveByUsernameOrNull(String username) {
        try {
            User user = userService.getByUsername(safeTrim(username));
            return Result.ok(toSummary(user));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserSummary> getByIdOrNull(int userId) {
        if (userId <= 0) {
            return Result.ok(null);
        }
        try {
            User user = userService.getById(userId);
            return Result.ok(toSummary(user));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<UserSummary>> batchSummary(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Result.ok(List.of());
        }
        try {
            LinkedHashSet<Integer> dedup = new LinkedHashSet<>();
            for (Integer id : userIds) {
                if (id == null || id <= 0) {
                    continue;
                }
                dedup.add(id);
                if (dedup.size() >= 200) {
                    break;
                }
            }
            if (dedup.isEmpty()) {
                return Result.ok(List.of());
            }

            List<User> users = internalUserService.batchGetUserSummaries(new ArrayList<>(dedup));
            if (users == null || users.isEmpty()) {
                return Result.ok(List.of());
            }
            List<UserSummary> list = new ArrayList<>(users.size());
            for (User u : users) {
                if (u == null || u.getId() <= 0) {
                    continue;
                }
                list.add(toSummary(u));
            }
            return Result.ok(list);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private UserSummary toSummary(User user) {
        if (user == null) {
            return null;
        }
        UserSummary resp = new UserSummary();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setHeaderUrl(user.getHeaderUrl());
        return resp;
    }

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();
        return Result.error(ec.getCode(), msg);
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
