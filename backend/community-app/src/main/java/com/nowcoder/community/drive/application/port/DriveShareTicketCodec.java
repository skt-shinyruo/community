package com.nowcoder.community.drive.application.port;

import java.time.Instant;

public interface DriveShareTicketCodec {

    String issue(String shareToken, Instant expiresAt);

    boolean valid(String shareToken, String ticket, Instant now);
}
