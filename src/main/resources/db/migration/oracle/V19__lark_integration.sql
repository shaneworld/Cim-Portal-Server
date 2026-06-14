ALTER TABLE security_setting ADD (
  lark_base_url         VARCHAR2(512) DEFAULT 'https://open.feishu.cn',
  lark_app_id           VARCHAR2(255),
  lark_app_secret       VARCHAR2(512),
  lark_receiver_id      VARCHAR2(255),
  lark_receiver_id_type VARCHAR2(16) DEFAULT 'email'
);
