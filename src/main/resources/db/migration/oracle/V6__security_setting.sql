CREATE TABLE security_setting (
  id                     NUMBER(19)    NOT NULL PRIMARY KEY,
  sso_enabled            NUMBER(1)     DEFAULT 0 NOT NULL,
  sso_issuer_uri         VARCHAR2(512) NULL,
  sso_client_id          VARCHAR2(255) NULL,
  sso_scopes             VARCHAR2(255) DEFAULT 'openid profile' NOT NULL,
  sso_username_claim     VARCHAR2(64)  DEFAULT 'preferred_username' NOT NULL,
  internal_password_hash VARCHAR2(100) NULL,
  updated_at             TIMESTAMP     NULL
);

INSERT INTO security_setting (id, sso_enabled, sso_scopes, sso_username_claim, internal_password_hash)
  VALUES (1, 0, 'openid profile', 'preferred_username',
          '$2a$10$celdHPCndrjS1gyf6jvcAe4/soZAQL/mKHsqdX5af/OCTFihFuO.y');
