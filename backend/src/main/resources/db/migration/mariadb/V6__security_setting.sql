CREATE TABLE security_setting (
  id                     BIGINT       NOT NULL PRIMARY KEY,
  sso_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
  sso_issuer_uri         VARCHAR(512) NULL,
  sso_client_id          VARCHAR(255) NULL,
  sso_scopes             VARCHAR(255) NOT NULL DEFAULT 'openid profile',
  sso_username_claim     VARCHAR(64)  NOT NULL DEFAULT 'preferred_username',
  internal_password_hash VARCHAR(100) NULL,
  updated_at             TIMESTAMP    NULL
);

INSERT INTO security_setting (id, sso_enabled, sso_scopes, sso_username_claim, internal_password_hash)
  VALUES (1, FALSE, 'openid profile', 'preferred_username',
          '$2a$10$celdHPCndrjS1gyf6jvcAe4/soZAQL/mKHsqdX5af/OCTFihFuO.y');
