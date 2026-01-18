package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.CaptchaProperties;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class CaptchaService {

    private static final SecureRandom random = new SecureRandom();

    private final CaptchaProperties properties;
    private final CaptchaStore captchaStore;

    public CaptchaService(CaptchaProperties properties, CaptchaStore captchaStore) {
        this.properties = properties;
        this.captchaStore = captchaStore;
    }

    public IssuedCaptcha issue() throws Exception {
        String captchaId = uuid();
        String code = isBlank(properties.getFixedCode()) ? randomCode(4) : properties.getFixedCode().trim();

        int ttlSeconds = Math.max(1, properties.getTtlSeconds());
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        captchaStore.save(captchaId, code, ttl);

        BufferedImage image = render(code);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        String imageBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return new IssuedCaptcha(captchaId, imageBase64, ttlSeconds);
    }

    public boolean verify(String captchaId, String code) {
        if (isBlank(captchaId) || isBlank(code)) {
            return false;
        }
        String expected = captchaStore.get(captchaId);
        if (isBlank(expected)) {
            return false;
        }
        boolean ok = expected.equalsIgnoreCase(code.trim());
        if (ok) {
            // 一次性验证码：校验成功后立即失效，降低重放风险
            captchaStore.delete(captchaId);
            return true;
        }

        int ttlSeconds = Math.max(1, properties.getTtlSeconds());
        int maxFailures = Math.max(1, properties.getMaxFailures());
        int failures = captchaStore.incrementFailures(captchaId, Duration.ofSeconds(ttlSeconds));
        if (failures >= maxFailures) {
            // 失败次数达到阈值：作废该验证码，要求重新获取
            captchaStore.delete(captchaId);
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

    public record IssuedCaptcha(String captchaId, String imageBase64, int ttlSeconds) {
    }
}
