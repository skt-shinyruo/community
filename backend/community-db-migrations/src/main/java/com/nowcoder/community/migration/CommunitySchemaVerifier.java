package com.nowcoder.community.migration;

final class CommunitySchemaVerifier {

    private CommunitySchemaVerifier() {
    }

    static void verifyExactV001(String jdbcUrl, String username, String password) {
        CommunitySchemaCatalog expected = CommunitySchemaCatalog.canonical();
        CommunitySchemaCatalog actual = CommunitySchemaCatalog.capture(jdbcUrl, username, password);
        if (!actual.equals(expected)) {
            throw new CommunitySchemaMismatchException(
                    "Community schema does not exactly match V001: " + actual.differenceFrom(expected));
        }
    }
}
