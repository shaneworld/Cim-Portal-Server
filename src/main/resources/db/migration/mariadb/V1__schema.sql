CREATE TABLE enum_value (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  category    VARCHAR(32)  NOT NULL,
  code        VARCHAR(64)  NOT NULL,
  label_zh    VARCHAR(255) NOT NULL,
  label_en    VARCHAR(255) NOT NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  active      TINYINT(1)   NOT NULL DEFAULT 1,
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_enum_category_code UNIQUE (category, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(64)  NOT NULL,
  name_zh        VARCHAR(255) NOT NULL,
  name_en        VARCHAR(255) NOT NULL,
  url            VARCHAR(1024) NOT NULL,
  icon           VARCHAR(64)  NOT NULL,
  category_code  VARCHAR(64)  NOT NULL,
  status_code    VARCHAR(64)  NOT NULL,
  sort_order     INT          NOT NULL DEFAULT 0,
  open_in_new_tab TINYINT(1)  NOT NULL DEFAULT 1,
  created_at     DATETIME     NOT NULL,
  updated_at     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_link_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link_access_grant (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  link_id     BIGINT      NOT NULL,
  grant_type  VARCHAR(16) NOT NULL,
  grant_code  VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_grant UNIQUE (link_id, grant_type, grant_code),
  CONSTRAINT fk_grant_link FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE label (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  label_key   VARCHAR(128) NOT NULL,
  type        VARCHAR(32)  NOT NULL,
  text_zh     VARCHAR(1024) NOT NULL,
  text_en     VARCHAR(1024) NOT NULL,
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_label_key UNIQUE (label_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_info (
  employee_id     VARCHAR(64)  NOT NULL,
  display_name_zh VARCHAR(255) NOT NULL,
  display_name_en VARCHAR(255) NOT NULL,
  department_code VARCHAR(64)  NOT NULL,
  role_code       VARCHAR(64)  NOT NULL,
  email           VARCHAR(255),
  active          TINYINT(1)   NOT NULL DEFAULT 1,
  synced_at       DATETIME     NOT NULL,
  PRIMARY KEY (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
