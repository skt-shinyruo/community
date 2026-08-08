package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.user.domain.model.UserRole;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class UserCredentialDomainService {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile(
            "\\A\\$2[aby]\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}\\z"
    );

    private final UsernamePolicyDomainService usernamePolicyDomainService;

    public UserCredentialDomainService() {
        this(new UsernamePolicyDomainService());
    }

    public UserCredentialDomainService(UsernamePolicyDomainService usernamePolicyDomainService) {
        this.usernamePolicyDomainService = usernamePolicyDomainService;
    }

    public String trim(String value) {
        return usernamePolicyDomainService.trim(value);
    }

    public boolean isSafeUsername(String value) {
        return usernamePolicyDomainService.isSafe(value);
    }

    public String canonicalEmail(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    public boolean isBcrypt(String stored) {
        return hasText(stored) && BCRYPT_PATTERN.matcher(stored).matches();
    }

    public List<String> authoritiesForType(int type) {
        return UserRole.requireValid(type).authorities();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
