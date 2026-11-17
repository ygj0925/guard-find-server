-- liquibase formatted sql
-- changeset guardfind:case-erasure-snapshot-repair-1
-- Scrub all revisions, including logically deleted revisions, for previously erased cases.
UPDATE gf_case_version
SET snapshot_json = '{}'
WHERE case_id IN (SELECT id FROM gf_find_case WHERE status = 'ERASED');
