package com.nowcoder.community.common.api;

public interface ErrorCode {

    int getCode();

    String getMessage();

    /**
     * 映射到 HTTP Status（用于统一错误协议：HTTP status 表达“错误类别”，Result.code 表达“业务细分”）。
     *
     * <p>默认策略：
     * <ul>
     *   <li>若 code 落在 4xx/5xx，认为本身就是 HTTP 语义码，直接使用。</li>
     *   <li>否则默认 500（fail-closed）。</li>
     * </ul>
     * 业务错误码（如 10001）应显式覆盖该方法。</p>
     */
    default int getHttpStatus() {
        int code = getCode();
        if (code >= 400 && code < 600) {
            return code;
        }
        return 500;
    }
}
