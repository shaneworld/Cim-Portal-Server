ALTER TABLE duty_line ADD (schedule_name VARCHAR2(255));
ALTER TABLE security_setting ADD (
  duty_api_base_url VARCHAR2(512),
  duty_api_key      VARCHAR2(512)
);
