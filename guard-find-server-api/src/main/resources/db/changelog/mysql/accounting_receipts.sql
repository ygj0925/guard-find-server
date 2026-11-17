--liquibase formatted sql
--changeset jianzhang:accounting-receipts-1 dbms:mysql
CREATE TABLE gf_accounting_receipt (
 id VARCHAR(36) NOT NULL COMMENT '票据UUID',
 user_id BIGINT NOT NULL COMMENT '所属App用户',
 name VARCHAR(120) NOT NULL COMMENT '原始文件名',
 mime VARCHAR(32) NOT NULL COMMENT '经过文件头验证的内容类型',
 size BIGINT NOT NULL COMMENT '文件字节数',
 content MEDIUMBLOB NOT NULL COMMENT '私有票据二进制',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
 PRIMARY KEY(id), KEY idx_accounting_receipt_user(user_id)
) COMMENT='简账私有票据';
