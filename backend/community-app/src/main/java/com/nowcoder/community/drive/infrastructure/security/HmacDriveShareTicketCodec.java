package com.nowcoder.community.drive.infrastructure.security;

import com.nowcoder.community.drive.application.port.DriveShareTicketCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Component
public class HmacDriveShareTicketCodec implements DriveShareTicketCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DEV_SECRET = "community-drive-dev-secret";

    private final String secret;

    public HmacDriveShareTicketCodec(@Value("${drive.share.ticket-secret:" + DEV_SECRET + "}") String secret) {
        this.secret = secret == null || secret.isBlank() ? DEV_SECRET : secret.trim();
    }

    @Override
    public String issue(String shareToken, Instant expiresAt) {
        if (shareToken == null || shareToken.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("shareToken/expiresAt must not be blank");
        }
        String payloadPrefix = shareToken + ":" + expiresAt.getEpochSecond();
        String payload = payloadPrefix + ":" + hmac(payloadPrefix);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean valid(String shareToken, String ticket, Instant now) {
        if (shareToken == null || shareToken.isBlank() || ticket == null || ticket.isBlank() || now == null) {
            return false;
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(ticket), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String[] parts = payload.split(":", 3);
        if (parts.length != 3 || !Objects.equals(shareToken, parts[0])) {
            return false;
        }
        long expiresAtEpochSecond;
        try {
            expiresAtEpochSecond = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (!now.isBefore(Instant.ofEpochSecond(expiresAtEpochSecond))) {
            return false;
        }
        String expected = hmac(parts[0] + ":" + parts[1]);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("cannot sign drive share ticket", e);
        }
    }
}
