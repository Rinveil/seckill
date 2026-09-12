package com.seckill.gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "seckill")
public class SeckillGatewayProperties {

    private final Jwt jwt = new Jwt();
    private final Auth auth = new Auth();

    public Jwt getJwt() {
        return jwt;
    }

    public Auth getAuth() {
        return auth;
    }

    public static class Jwt {
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Auth {
        private List<String> whitelist = new ArrayList<>();

        public List<String> getWhitelist() {
            return whitelist;
        }

        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist;
        }
    }
}
