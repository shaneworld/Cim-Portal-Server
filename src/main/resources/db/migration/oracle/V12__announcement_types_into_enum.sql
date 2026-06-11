ALTER TABLE enum_value ADD (color VARCHAR2(16) DEFAULT NULL NULL, icon VARCHAR2(64) DEFAULT NULL NULL);

INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at)
  SELECT 'ANNOUNCEMENT_TYPE', code, label_zh, label_en, sort_order, active, color, icon, SYSTIMESTAMP, SYSTIMESTAMP
  FROM announcement_type;

INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at)
  VALUES ('ANNOUNCEMENT_TYPE','GENERAL','一般通知','General Notice',40,1,'slate','megaphone',SYSTIMESTAMP,SYSTIMESTAMP);

DROP TABLE announcement_type;

ALTER TABLE security_setting DROP (announcements_enabled, duty_lines_enabled);
ALTER TABLE security_setting ADD (info_panel_enabled NUMBER(1) DEFAULT 1 NOT NULL);
