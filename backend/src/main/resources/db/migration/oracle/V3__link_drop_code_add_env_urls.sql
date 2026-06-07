-- V3: Remove the link.code field (+ unique constraint) and add per-environment URL columns.
-- Plain links use url; env-aware links use url_dev/url_uat/url_release with url NULL.
ALTER TABLE link DROP CONSTRAINT uk_link_code;
ALTER TABLE link DROP COLUMN code;
ALTER TABLE link MODIFY (url VARCHAR2(1024) NULL);
ALTER TABLE link ADD (url_dev VARCHAR2(1024), url_uat VARCHAR2(1024), url_release VARCHAR2(1024));
