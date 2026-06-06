-- label subsystem removed; drop the redundant i18n label table.
-- Guarded so re-runs / absent table do not fail (ORA-00942 = table does not exist).
BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE label';
EXCEPTION WHEN OTHERS THEN
  IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
