package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.command.TriggerCompensationCommand;
import com.nowcoder.community.ops.application.result.CompensationTriggerResult;

public interface CompensationTriggerPort {

    CompensationTriggerResult trigger(TriggerCompensationCommand command);
}
