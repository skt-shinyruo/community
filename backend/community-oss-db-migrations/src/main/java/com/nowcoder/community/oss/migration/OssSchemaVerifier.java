package com.nowcoder.community.oss.migration;

final class OssSchemaVerifier {

    private OssSchemaVerifier() {
    }

    static void verifyExactV001(String jdbcUrl, String username, String password) {
        OssSchemaCatalog expected = OssSchemaCatalog.canonical();
        OssSchemaCatalog actual = OssSchemaCatalog.capture(jdbcUrl, username, password);
        if (!actual.equals(expected)) {
            throw new OssSchemaMismatchException(
                    "Community OSS schema does not exactly match V001: " + actual.differenceFrom(expected));
        }
    }
}
