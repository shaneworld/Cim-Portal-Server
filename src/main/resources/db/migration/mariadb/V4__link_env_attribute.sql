UPDATE link SET url = COALESCE(url, url_release, url_uat, url_dev) WHERE url IS NULL;
ALTER TABLE link DROP COLUMN url_dev, DROP COLUMN url_uat, DROP COLUMN url_release;
ALTER TABLE link ADD COLUMN environment VARCHAR(16) NULL;
ALTER TABLE link MODIFY url VARCHAR(1024) NOT NULL;
