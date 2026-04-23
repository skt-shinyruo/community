package com.nowcoder.community.im.realtime.session;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.im.common.session.OpenImSessionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/im/sessions")
public class ImSessionApiController {

    private final ImSessionService imSessionService;

    public ImSessionApiController(ImSessionService imSessionService) {
        this.imSessionService = imSessionService;
    }

    @PostMapping
    public Result<OpenImSessionResponse> openSession(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return Result.ok(imSessionService.openSession(authorizationHeader));
    }
}
