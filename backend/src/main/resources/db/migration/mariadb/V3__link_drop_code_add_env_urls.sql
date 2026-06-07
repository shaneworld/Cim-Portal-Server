-- V3: Remove the link.code field (+ unique index) and add per-environment URL columns.
-- Plain links use url; env-aware links use url_dev/url_uat/url_release with url NULL.
ALTER TABLE link DROP INDEX uk_link_code;
ALTER TABLE link DROP COLUMN code;
ALTER TABLE link MODIFY url VARCHAR(1024) NULL;
ALTER TABLE link ADD COLUMN url_dev     VARCHAR(1024) NULL,
                 ADD COLUMN url_uat     VARCHAR(1024) NULL,
                 ADD COLUMN url_release VARCHAR(1024) NULL;
