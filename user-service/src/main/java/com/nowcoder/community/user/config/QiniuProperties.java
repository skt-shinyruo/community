package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qiniu")
public class QiniuProperties {

    private final Key key = new Key();
    private final Bucket bucket = new Bucket();

    public Key getKey() {
        return key;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public static class Key {
        private String access;
        private String secret;

        public String getAccess() {
            return access;
        }

        public void setAccess(String access) {
            this.access = access;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Bucket {
        private final Header header = new Header();

        public Header getHeader() {
            return header;
        }

        public static class Header {
            private String name;
            private String url;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }
        }
    }
}

