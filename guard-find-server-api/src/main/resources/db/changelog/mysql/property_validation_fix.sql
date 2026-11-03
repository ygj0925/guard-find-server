-- liquibase formatted sql
-- changeset guardfind:property-image-capacity-fix-1
ALTER TABLE gf_property MODIFY COLUMN images TEXT DEFAULT NULL COMMENT '图片 URL JSON 数组，最多9张';
