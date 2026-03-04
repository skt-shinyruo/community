package com.nowcoder.community.infra.startup;

import org.springframework.core.env.Environment;

import java.util.List;

public interface StartupValidator {

    void validate(Environment environment, List<String> errors);
}

