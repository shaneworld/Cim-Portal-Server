package com.cimportal.common;

/** 集中跨切面常量(profile/角色/安全路径/dev token TTL)。 */
public final class AppConstants {
    private AppConstants() {}

    public static final class Profiles {
        public static final String DEV = "dev";
        public static final String UAT = "uat";
        public static final String PROD = "prod";
        public static final String TEST = "test";
        private Profiles() {}
    }

    public static final class Roles {
        public static final String PORTAL_ADMIN = "PORTAL_ADMIN";
        private Roles() {}
    }

    public static final class Paths {
        public static final String DEV_TOKEN = "/dev/token";
        public static final String ADMIN_API = "/api/admin/**";
        public static final String PORTAL_CONFIG = "/api/portal/config";
        private Paths() {}
    }

    public static final class DevToken {
        public static final long TTL_HOURS = 12;
        private DevToken() {}
    }

    public static final class Issuer {
        /** iss claim value minted by the portal's own JwtEncoder. */
        public static final String PORTAL = "cim-portal";
        private Issuer() {}
    }

    public static final class Auth {
        public static final String LOGIN_PATH = "/api/auth/login";
        private Auth() {}
    }
}
