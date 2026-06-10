UPDATE link SET url = COALESCE(url, url_release, url_uat, url_dev) WHERE url IS NULL;
ALTER TABLE link DROP (url_dev, url_uat, url_release);
ALTER TABLE link ADD (environment VARCHAR2(16));
ALTER TABLE link MODIFY (url VARCHAR2(1024) NOT NULL);
