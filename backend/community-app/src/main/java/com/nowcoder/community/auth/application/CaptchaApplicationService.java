package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.IssueCaptchaCommand;
import com.nowcoder.community.auth.application.result.CaptchaIssueResult;
import com.nowcoder.community.auth.config.CaptchaProperties;
import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
import com.nowcoder.community.auth.domain.service.CaptchaDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class CaptchaApplicationService {

    private static final SecureRandom random = new SecureRandom();
    private static final String RATE_LIMIT_IP_KEY_PREFIX = "auth:captcha:issue:ip:";

    private final CaptchaProperties properties;
    private final CaptchaRepository captchaStore;
    private final CaptchaDomainService captchaDomainService;

    public CaptchaApplicationService(
            CaptchaProperties properties,
            CaptchaRepository captchaStore,
            CaptchaDomainService captchaDomainService
    ) {
        this.properties = properties;
        this.captchaStore = captchaStore;
        this.captchaDomainService = captchaDomainService;
    }

    public CaptchaIssueResult issue(IssueCaptchaCommand command) {
        enforceIssueRateLimit(command == null ? null : command.clientIp());
        IssuedCaptcha issued = issue();
        return new CaptchaIssueResult(issued.captchaId(), issued.imageBase64(), issued.ttlSeconds());
    }

    public IssuedCaptcha issue() {
        String captchaId = uuid();
        String code = isBlank(properties.getFixedCode()) ? randomCode(4) : properties.getFixedCode().trim();

        int ttlSeconds = Math.max(1, properties.getTtlSeconds());
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        try {
            captchaStore.save(captchaId, code, ttl);
        } catch (RuntimeException e) {
            throw captchaUnavailable(e);
        }

        BufferedImage image = render(code);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
        } catch (IOException e) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_GENERATE_FAILED);
        }
        String imageBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return new IssuedCaptcha(captchaId, imageBase64, ttlSeconds);
    }

    public boolean verify(String captchaId, String code) {
        if (isBlank(captchaId) || isBlank(code)) {
            return false;
        }
        int ttlSeconds = Math.max(1, properties.getTtlSeconds());
        int maxFailures = Math.max(1, properties.getMaxFailures());
        try {
            CaptchaRepository.VerifyResult verifyResult = captchaStore.verifyAndConsume(captchaId, captchaDomainService.normalizeCode(code));
            if (verifyResult == CaptchaRepository.VerifyResult.MATCHED) {
                return true;
            }
            if (verifyResult == CaptchaRepository.VerifyResult.NOT_FOUND) {
                return false;
            }
            int failures = captchaStore.incrementFailures(captchaId, Duration.ofSeconds(ttlSeconds));
            if (failures >= maxFailures) {
                // 失败次数达到阈值：作废该验证码，要求重新获取
                captchaStore.delete(captchaId);
            }
        } catch (RuntimeException e) {
            throw captchaUnavailable(e);
        }
        return false;
    }

    private String randomCode(int length) {
        int n = Math.max(4, length);
        String chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private BufferedImage render(String text) {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            g.setFont(new Font("Arial", Font.BOLD, 26));
            g.setColor(new Color(20, 40, 60));
            g.drawString(text, 14, 28);

            // noise lines
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(random.nextInt(160), random.nextInt(160), random.nextInt(160)));
                int x1 = random.nextInt(width);
                int y1 = random.nextInt(height);
                int x2 = random.nextInt(width);
                int y2 = random.nextInt(height);
                g.drawLine(x1, y1, x2, y2);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private BusinessException captchaUnavailable(RuntimeException cause) {
        return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "验证码服务暂时不可用，请稍后重试", cause);
    }

    private void enforceIssueRateLimit(String clientIp) {
        if (captchaStore == null || !StringUtils.hasText(clientIp)) {
            return;
        }
        int windowSeconds = Math.max(1, properties.getTtlSeconds());
        int maxRequestsPerIp = properties.getMaxIssueRequestsPerIp();
        if (maxRequestsPerIp <= 0) {
            return;
        }
        String ip = clientIp.trim();
        String rateLimitKey = RATE_LIMIT_IP_KEY_PREFIX + ip;
        int count;
        try {
            count = captchaStore.incrementFailures(rateLimitKey, Duration.ofSeconds(windowSeconds));
        } catch (RuntimeException e) {
            throw captchaUnavailable(e);
        }
        if (count > maxRequestsPerIp) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "验证码获取过于频繁，请稍后再试");
        }
    }

    public record IssuedCaptcha(String captchaId, String imageBase64, int ttlSeconds) {
    }
}
