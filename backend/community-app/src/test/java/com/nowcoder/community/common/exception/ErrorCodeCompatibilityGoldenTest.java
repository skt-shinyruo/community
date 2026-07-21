package com.nowcoder.community.common.exception;

import com.nowcoder.community.analytics.exception.AnalyticsErrorCode;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.drive.exception.DriveErrorCode;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.market.exception.MarketErrorCode;
import com.nowcoder.community.search.exception.SearchErrorCode;
import com.nowcoder.community.social.exception.SocialErrorCode;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import com.nowcoder.community.common.web.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeCompatibilityGoldenTest {

    private static final List<Class<? extends Enum<?>>> ERROR_ENUMS = List.of(
            CommonErrorCode.class,
            AnalyticsErrorCode.class,
            AuthErrorCode.class,
            ContentErrorCode.class,
            DriveErrorCode.class,
            GrowthErrorCode.class,
            MarketErrorCode.class,
            SearchErrorCode.class,
            SocialErrorCode.class,
            UserErrorCode.class,
            WalletErrorCode.class
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void errorCodeAndTransportResultMustRemainWireCompatible(Golden golden) throws Exception {
        ErrorCode errorCode = golden.errorCode();

        assertThat(errorCode.getCode()).isEqualTo(golden.code());
        assertThat(errorCode.getMessage()).isEqualTo(golden.message());
        assertLegacyStatusWhenStillPresent(errorCode, golden.httpStatus());
        assertKindWhenPresent(errorCode, golden.kind());

        Result<Void> body = Result.error(golden.code(), golden.message(), golden.httpStatus());
        assertThat(body.getCode()).isEqualTo(golden.code());
        assertThat(body.getMessage()).isEqualTo(golden.message());
        assertThat(body.getHttpStatus()).isEqualTo(golden.httpStatus());
    }

    @Test
    void goldenTableMustCoverEveryPublishedErrorEnumConstantExactlyOnce() {
        Set<ErrorCode> actual = new LinkedHashSet<>();
        for (Class<? extends Enum<?>> enumType : ERROR_ENUMS) {
            Arrays.stream(enumType.getEnumConstants())
                    .map(ErrorCode.class::cast)
                    .forEach(actual::add);
        }
        Set<ErrorCode> golden = goldenCases()
                .map(arguments -> (Golden) arguments.get()[0])
                .map(Golden::errorCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(golden).containsExactlyInAnyOrderElementsOf(actual);
    }

    @Test
    void successResultMustKeepItsExistingWireShape() {
        Result<String> result = Result.ok("ok");

        assertThat(result.getCode()).isZero();
        assertThat(result.getMessage()).isEqualTo("OK");
        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("ok");
    }

    private static Stream<Arguments> goldenCases() {
        return Stream.of(
                golden(CommonErrorCode.INVALID_ARGUMENT, 400, "参数错误", "INVALID_INPUT", 400),
                golden(CommonErrorCode.UNAUTHORIZED, 401, "未登录或登录已失效", "UNAUTHENTICATED", 401),
                golden(CommonErrorCode.FORBIDDEN, 403, "无权限访问", "FORBIDDEN", 403),
                golden(CommonErrorCode.NOT_FOUND, 404, "资源不存在", "NOT_FOUND", 404),
                golden(CommonErrorCode.TOO_MANY_REQUESTS, 429, "请求过于频繁", "THROTTLED", 429),
                golden(CommonErrorCode.SERVICE_UNAVAILABLE, 503, "服务不可用", "UNAVAILABLE", 503),
                golden(CommonErrorCode.INTERNAL_ERROR, 500, "服务端异常", "INTERNAL", 500),

                golden(AnalyticsErrorCode.COUNTER_UNAVAILABLE, 16001, "统计存储不可用", "UNAVAILABLE", 503),
                golden(AnalyticsErrorCode.INTERNAL_ERROR, 16002, "统计服务异常", "INTERNAL", 500),
                golden(AnalyticsErrorCode.RANGE_INVALID, 16003, "查询区间不合法", "INVALID_INPUT", 400),

                golden(AuthErrorCode.INVALID_CREDENTIALS, 10001, "用户名或密码错误", "UNAUTHENTICATED", 401),
                golden(AuthErrorCode.USER_DISABLED, 10002, "账号未激活或被禁用", "FORBIDDEN", 403),
                golden(AuthErrorCode.TOKEN_INVALID, 10003, "令牌无效或已过期", "UNAUTHENTICATED", 401),
                golden(AuthErrorCode.REFRESH_TOKEN_INVALID, 10004, "刷新令牌无效或已过期", "UNAUTHENTICATED", 401),
                golden(AuthErrorCode.CAPTCHA_REQUIRED, 10005, "需要验证码", "INVALID_INPUT", 400),
                golden(AuthErrorCode.CAPTCHA_INVALID, 10006, "验证码不正确或已失效", "INVALID_INPUT", 400),
                golden(AuthErrorCode.PASSWORD_RESET_INVALID, 10007, "重置凭证无效或已过期", "INVALID_INPUT", 400),
                golden(AuthErrorCode.CAPTCHA_GENERATE_FAILED, 10008, "验证码生成失败", "INTERNAL", 500),
                golden(AuthErrorCode.REGISTRATION_CODE_INVALID, 10009, "注册验证码不正确", "INVALID_INPUT", 400),
                golden(AuthErrorCode.REGISTRATION_CODE_EXPIRED, 10010, "注册验证码已过期", "INVALID_INPUT", 400),
                golden(AuthErrorCode.REGISTRATION_CODE_RESEND_COOLDOWN, 10011, "注册验证码发送过于频繁", "THROTTLED", 429),
                golden(AuthErrorCode.REGISTRATION_CODE_TOO_MANY_ATTEMPTS, 10012, "注册验证码错误次数过多，请重新获取", "INVALID_INPUT", 400),
                golden(AuthErrorCode.REGISTRATION_CONTEXT_INVALID, 10013, "注册上下文已失效，请重新注册", "NOT_FOUND", 404),
                golden(AuthErrorCode.REGISTRATION_ACTIVATED_LOGIN_REQUIRED, 10014, "注册已完成，请直接登录", "CONFLICT", 409),

                golden(ContentErrorCode.POST_NOT_FOUND, 12001, "帖子不存在", "NOT_FOUND", 404),
                golden(ContentErrorCode.COMMENT_NOT_FOUND, 12002, "评论不存在", "NOT_FOUND", 404),
                golden(ContentErrorCode.CATEGORY_NOT_FOUND, 12003, "分类不存在", "NOT_FOUND", 404),
                golden(ContentErrorCode.TAG_NOT_FOUND, 12004, "标签不存在", "NOT_FOUND", 404),
                golden(ContentErrorCode.BOOKMARK_CONFLICT, 12005, "收藏状态冲突", "CONFLICT", 409),
                golden(ContentErrorCode.SUBSCRIPTION_CONFLICT, 12006, "订阅状态冲突", "CONFLICT", 409),
                golden(ContentErrorCode.CONTENT_RENDER_FAILED, 12007, "内容渲染失败", "INTERNAL", 500),
                golden(ContentErrorCode.INTERNAL_ERROR, 12008, "内容服务异常", "INTERNAL", 500),
                golden(ContentErrorCode.REQUEST_REPLAY_CONFLICT, 12009, "请求号与已有内容请求不一致", "CONFLICT", 409),
                golden(ContentErrorCode.MODERATION_DECISION_CONFLICT, 12010, "治理决定与已有处置冲突", "CONFLICT", 409),

                golden(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, 16001, "网盘空间不存在", "NOT_FOUND", 404),
                golden(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, 16002, "网盘条目不存在", "NOT_FOUND", 404),
                golden(DriveErrorCode.DRIVE_PARENT_NOT_FOUND, 16003, "目标文件夹不存在", "NOT_FOUND", 404),
                golden(DriveErrorCode.DRIVE_DUPLICATE_NAME, 16004, "同名文件或文件夹已存在", "CONFLICT", 409),
                golden(DriveErrorCode.DRIVE_QUOTA_EXCEEDED, 16005, "网盘容量不足", "CONFLICT", 409),
                golden(DriveErrorCode.DRIVE_INVALID_MOVE, 16006, "不能移动到自身或子目录", "INVALID_INPUT", 400),
                golden(DriveErrorCode.DRIVE_ENTRY_TRASHED, 16007, "回收站条目不可执行该操作", "CONFLICT", 409),
                golden(DriveErrorCode.DRIVE_SHARE_INVALID, 16008, "分享链接不可用", "NOT_FOUND", 404),
                golden(DriveErrorCode.DRIVE_SHARE_PASSWORD_INVALID, 16009, "提取码错误", "FORBIDDEN", 403),
                golden(DriveErrorCode.DRIVE_UPLOAD_INVALID, 16010, "上传会话不可用", "CONFLICT", 409),
                golden(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, 16011, "网盘存储服务不可用", "UNAVAILABLE", 503),

                golden(GrowthErrorCode.INVALID_REQUEST, 16001, "成长中心请求参数错误", "INVALID_INPUT", 400),
                golden(GrowthErrorCode.TARGET_USER_NOT_FOUND, 16002, "目标用户不存在", "NOT_FOUND", 404),
                golden(MarketErrorCode.REQUEST_REPLAY_CONFLICT, 18001, "请求号与已有市场订单不一致", "CONFLICT", 409),
                golden(MarketErrorCode.ORDER_TRANSITION_CONFLICT, 18002, "市场订单状态已发生变化", "CONFLICT", 409),
                golden(SearchErrorCode.INDEX_UNAVAILABLE, 15001, "搜索索引不可用", "UNAVAILABLE", 503),
                golden(SearchErrorCode.INTERNAL_ERROR, 15003, "搜索服务异常", "INTERNAL", 500),

                golden(SocialErrorCode.CANNOT_FOLLOW_SELF, 13001, "不能关注自己", "INVALID_INPUT", 400),
                golden(SocialErrorCode.CANNOT_BLOCK_SELF, 13002, "不能拉黑自己", "INVALID_INPUT", 400),
                golden(SocialErrorCode.LIKE_CONFLICT, 13003, "点赞状态冲突", "CONFLICT", 409),
                golden(SocialErrorCode.FOLLOW_CONFLICT, 13004, "关注状态冲突", "CONFLICT", 409),
                golden(SocialErrorCode.BLOCK_CONFLICT, 13005, "拉黑状态冲突", "CONFLICT", 409),
                golden(SocialErrorCode.INTERNAL_ERROR, 13006, "社交服务异常", "INTERNAL", 500),

                golden(UserErrorCode.USER_NOT_FOUND, 11001, "用户不存在", "NOT_FOUND", 404),
                golden(UserErrorCode.USER_ALREADY_EXISTS, 11002, "用户已存在", "CONFLICT", 409),
                golden(UserErrorCode.EMAIL_ALREADY_EXISTS, 11003, "邮箱已被注册", "CONFLICT", 409),
                golden(UserErrorCode.AVATAR_FILE_INVALID, 11004, "头像文件不合法", "INVALID_INPUT", 400),
                golden(UserErrorCode.AVATAR_SAVE_FAILED, 11005, "保存头像失败", "INTERNAL", 500),
                golden(UserErrorCode.STORAGE_UNAVAILABLE, 11006, "用户存储不可用", "UNAVAILABLE", 503),
                golden(UserErrorCode.INTERNAL_ERROR, 11007, "用户服务异常", "INTERNAL", 500),

                golden(WalletErrorCode.INVALID_REQUEST, 17001, "钱包请求参数错误", "INVALID_INPUT", 400),
                golden(WalletErrorCode.ACCOUNT_NOT_FOUND, 17002, "钱包账户不存在", "NOT_FOUND", 404),
                golden(WalletErrorCode.TXN_NOT_BALANCED, 17003, "钱包交易分录不平衡", "CONFLICT", 409),
                golden(WalletErrorCode.ACCOUNT_BALANCE_INSUFFICIENT, 17004, "钱包余额不足", "CONFLICT", 409),
                golden(WalletErrorCode.ACCOUNT_UPDATE_CONFLICT, 17005, "钱包账户更新冲突", "CONFLICT", 409),
                golden(WalletErrorCode.PLATFORM_CASH_INSUFFICIENT, 17006, "平台可提现现金不足", "CONFLICT", 409),
                golden(WalletErrorCode.REQUEST_REPLAY_CONFLICT, 17007, "请求号与已有钱包请求不一致", "CONFLICT", 409),
                golden(WalletErrorCode.INVALID_TRANSFER, 17008, "转账请求不合法", "INVALID_INPUT", 400),
                golden(WalletErrorCode.ACCOUNT_FROZEN, 17009, "钱包账户已冻结", "CONFLICT", 409)
        ).map(Arguments::of);
    }

    private static Golden golden(ErrorCode errorCode, int code, String message, String kind, int httpStatus) {
        return new Golden(errorCode, code, message, kind, httpStatus);
    }

    private static void assertLegacyStatusWhenStillPresent(ErrorCode errorCode, int expectedStatus) throws Exception {
        Method legacyMethod = Arrays.stream(ErrorCode.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("getHttpStatus"))
                .findFirst()
                .orElse(null);
        if (legacyMethod != null) {
            assertThat(legacyMethod.invoke(errorCode)).isEqualTo(expectedStatus);
        }
    }

    private static void assertKindWhenPresent(ErrorCode errorCode, String expectedKind) throws Exception {
        Method kindMethod = Arrays.stream(ErrorCode.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("getKind"))
                .findFirst()
                .orElse(null);
        if (kindMethod != null) {
            assertThat(kindMethod.invoke(errorCode)).hasToString(expectedKind);
        }
    }

    private record Golden(ErrorCode errorCode, int code, String message, String kind, int httpStatus) {
        @Override
        public String toString() {
            return errorCode.getClass().getSimpleName() + "." + errorCode;
        }
    }
}
