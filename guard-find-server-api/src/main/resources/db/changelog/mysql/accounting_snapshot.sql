--liquibase formatted sql
--changeset jianzhang:accounting-snapshot-1 dbms:mysql
CREATE TABLE gf_accounting_snapshot (
    user_id BIGINT NOT NULL COMMENT '所属 App 用户 ID',
    version BIGINT NOT NULL COMMENT '乐观锁版本',
    payload LONGTEXT NOT NULL COMMENT '简账版本化备份 JSON',
    operation_id VARCHAR(64) NOT NULL COMMENT '最近一次幂等操作 ID',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (user_id)
) COMMENT='简账个人账本云备份';
