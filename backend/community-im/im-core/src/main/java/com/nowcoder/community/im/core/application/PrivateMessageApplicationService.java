package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.core.service.PrivateMessageService;
import org.springframework.stereotype.Service;

@Service
public class PrivateMessageApplicationService {

    private final PrivateMessageService privateMessageService;

    public PrivateMessageApplicationService(PrivateMessageService privateMessageService) {
        this.privateMessageService = privateMessageService;
    }

    public PrivateMessagePersistedEvent persist(SendPrivateTextCommand command) {
        return privateMessageService.persist(command);
    }
}
