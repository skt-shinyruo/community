package com.nowcoder.community.im.migration;

final class ImSchemaVerifier {

    private ImSchemaVerifier() {
    }

    static void verifyExactV001(String jdbcUrl, String username, String password) {
        ImSchemaCatalog expected = ImSchemaCatalog.canonical();
        ImSchemaCatalog actual = ImSchemaCatalog.capture(jdbcUrl, username, password);
        if (!actual.equals(expected)) {
            throw new ImSchemaMismatchException(
                    "IM Core schema does not exactly match V001: " + actual.differenceFrom(expected));
        }
    }
}
