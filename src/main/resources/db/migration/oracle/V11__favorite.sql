CREATE TABLE favorite (
  employee_id VARCHAR2(64) NOT NULL,
  link_id     NUMBER       NOT NULL,
  created_at  TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_favorite PRIMARY KEY (employee_id, link_id),
  CONSTRAINT fk_favorite_link FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
);
