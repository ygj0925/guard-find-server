-- liquibase formatted sql

-- changeset guardfind:1
-- comment 天下无拐一期业务表：C 端账号、寻人档案、线索、消息、治理、采集
-- ----------------------------
-- C 端账号
-- ----------------------------
CREATE TABLE IF NOT EXISTS `gf_app_user` (
    `id`                   bigint(20)  AUTO_INCREMENT              COMMENT 'ID',
    `nickname`             varchar(30) NOT NULL                    COMMENT '昵称',
    `avatar`               varchar(500)  DEFAULT NULL              COMMENT '头像地址',
    `gender`               tinyint(1) UNSIGNED NOT NULL DEFAULT 0  COMMENT '性别（0：未知；1：男；2：女）',
    `phone`                varchar(255) DEFAULT NULL               COMMENT '手机号码（加密存储）',
    `password`             varchar(255) DEFAULT NULL               COMMENT '密码（BCRYPT）',
    `account_status`       tinyint(1)   UNSIGNED NOT NULL DEFAULT 1 COMMENT '账号状态（1：正常；2：锁定；3：注销申请中；4：已注销）',
    `identity_type`        varchar(16)  NOT NULL DEFAULT 'PUBLIC'  COMMENT '身份类型（GUARDIAN：家属；VOLUNTEER：志愿者；PUBLIC：普通用户）',
    `real_name_status`     varchar(16)  NOT NULL DEFAULT 'UNCHECKED' COMMENT '实名状态（UNCHECKED/CHECKING/PASSED/REJECTED）',
    `province_code`        varchar(12)    DEFAULT NULL             COMMENT '常驻省编码',
    `city_code`            varchar(12)    DEFAULT NULL             COMMENT '常驻市编码',
    `district_code`        varchar(12)    DEFAULT NULL             COMMENT '常驻区县编码',
    `volunteer_verified`   bit(1)       NOT NULL DEFAULT b'0'     COMMENT '志愿者是否已认证',
    `registration_source`  varchar(16)  NOT NULL DEFAULT 'APP'    COMMENT '注册来源（APP/COLLECT_IMPORT）',
    `last_active_time`     datetime       DEFAULT NULL             COMMENT '最后活跃时间',
    `deactivate_apply_time` datetime      DEFAULT NULL             COMMENT '注销申请时间（冷静期起点）',
    `create_user`          bigint(20)     DEFAULT NULL             COMMENT '创建人',
    `create_time`          datetime     NOT NULL                   COMMENT '创建时间',
    `update_user`          bigint(20)     DEFAULT NULL             COMMENT '修改人',
    `update_time`          datetime       DEFAULT NULL             COMMENT '修改时间',
    `deleted`              bigint(20)   NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_phone`(`phone`, `deleted`),
    INDEX `idx_account_status`(`account_status`),
    INDEX `idx_create_time`(`create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 用户表';

CREATE TABLE IF NOT EXISTS `gf_app_device` (
    `id`           bigint(20)  AUTO_INCREMENT              COMMENT 'ID',
    `user_id`      bigint(20)  NOT NULL                    COMMENT 'App 用户 ID',
    `platform`     varchar(16) NOT NULL                    COMMENT '平台（IOS/ANDROID/HARMONY/WEB）',
    `device_id`    varchar(128) NOT NULL                   COMMENT '设备标识',
    `device_name`  varchar(64)  DEFAULT NULL               COMMENT '设备名称',
    `push_token`   varchar(512) DEFAULT NULL               COMMENT '厂商推送 token（一期不写入，二期启用）',
    `app_version`  varchar(32)  DEFAULT NULL               COMMENT 'App 版本',
    `os_version`   varchar(32)  DEFAULT NULL               COMMENT '系统版本',
    `last_login_time` datetime  DEFAULT NULL               COMMENT '最后登录时间',
    `create_user`  bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`  datetime   NOT NULL                     COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`      bigint(20)  NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_device`(`user_id`, `device_id`, `deleted`),
    INDEX `idx_user_id`(`user_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 设备表';

-- ----------------------------
-- 寻人档案
-- ----------------------------
CREATE TABLE IF NOT EXISTS `gf_find_case` (
    `id`                  bigint(20)  AUTO_INCREMENT              COMMENT 'ID',
    `case_no`             varchar(32) NOT NULL                    COMMENT '档案编号（对外展示）',
    `owner_id`            bigint(20)    DEFAULT NULL              COMMENT '发布人（App 用户 ID；采集来源为空）',
    `status`              varchar(20)  NOT NULL DEFAULT 'DRAFT'   COMMENT '状态（DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED/WITHDRAWN/FOUND/ARCHIVED/OFFLINE/BLOCKED/ERASED）',
    `urgency_level`       varchar(16)  NOT NULL DEFAULT 'NORMAL'  COMMENT '紧急程度（NORMAL/URGENT/STANDING）',
    `relation_type`       varchar(16)    DEFAULT NULL             COMMENT '与本人关系（字典 relation_type）',
    `name`                varchar(64)  NOT NULL                   COMMENT '姓名',
    `gender`              tinyint(1) UNSIGNED NOT NULL DEFAULT 0  COMMENT '性别（0：未知；1：男；2：女）',
    `birthday`            date           DEFAULT NULL             COMMENT '出生日期',
    `age_at_missing`      int            DEFAULT NULL             COMMENT '失踪时年龄',
    `age_group`           varchar(16)    DEFAULT NULL             COMMENT '年龄段（字典 age_group）',
    `missing_time`        datetime         DEFAULT NULL           COMMENT '失踪时间',
    `province_code`       varchar(12)    DEFAULT NULL             COMMENT '失踪省编码',
    `city_code`           varchar(12)    DEFAULT NULL             COMMENT '失踪市编码',
    `district_code`       varchar(12)    DEFAULT NULL             COMMENT '失踪区县编码',
    `address`             varchar(500)   DEFAULT NULL             COMMENT '详细地址（加密存储，仅完整视图返回）',
    `hometown_code`       varchar(12)    DEFAULT NULL             COMMENT '户籍地区划编码',
    `lng`                 decimal(10, 6) DEFAULT NULL             COMMENT '经度（一期不采集，预留）',
    `lat`                 decimal(10, 6) DEFAULT NULL             COMMENT '纬度（一期不采集，预留）',
    `height`              int              DEFAULT NULL             COMMENT '失踪时身高（cm）',
    `body_type`           varchar(16)      DEFAULT NULL             COMMENT '体型',
    `hair_type`           varchar(16)      DEFAULT NULL             COMMENT '发型',
    `accent`              varchar(100)     DEFAULT NULL             COMMENT '口音',
    `features`            varchar(500)     DEFAULT NULL             COMMENT '疤痕/胎记等体貌特征',
    `clothing`            varchar(255)     DEFAULT NULL             COMMENT '失踪时衣着',
    `belongings`          varchar(255)     DEFAULT NULL             COMMENT '随身物品',
    `case_type`           varchar(20)    NOT NULL DEFAULT 'MISSING' COMMENT '失踪情形（字典 case_type）',
    `narrative`           text               DEFAULT NULL           COMMENT '发生经过',
    `family_clues`        text               DEFAULT NULL           COMMENT '家庭背景及线索资料',
    `other_note`          varchar(1000)      DEFAULT NULL           COMMENT '其他说明',
    `police_reported`     bit(1)             DEFAULT NULL           COMMENT '是否已报警',
    `police_station`      varchar(100)       DEFAULT NULL           COMMENT '受理派出所',
    `case_accepted_no`    varchar(64)        DEFAULT NULL           COMMENT '警方立案编号',
    `blood_sample_status` varchar(20)        DEFAULT 'UNKNOWN'      COMMENT '血样采集情况（NOT_COLLECTED/COLLECTED/UNKNOWN）',
    `follow_volunteer`    varchar(64)        DEFAULT NULL           COMMENT '跟进志愿者',
    `contact_mode`        varchar(16)      NOT NULL DEFAULT 'IN_APP' COMMENT '联络方式（IN_APP/PHONE）',
    `contact_phone`       varchar(255)       DEFAULT NULL           COMMENT '可公开联系电话（加密存储）',
    `found_time`          datetime           DEFAULT NULL           COMMENT '找回时间',
    `found_confirm_type`  varchar(16)        DEFAULT NULL           COMMENT '找回确认依据（POLICE/GUARDIAN/DNA）',
    `found_remark`        varchar(500)       DEFAULT NULL           COMMENT '找回说明',
    `audit_remark`        varchar(500)       DEFAULT NULL           COMMENT '最近一次审核意见',
    `publish_time`        datetime           DEFAULT NULL           COMMENT '发布时间',
    `last_confirm_time`   datetime           DEFAULT NULL           COMMENT '最后确认时间（时效计算基准）',
    `archived_time`       datetime           DEFAULT NULL           COMMENT '归档时间',
    `version`             int              NOT NULL DEFAULT 0       COMMENT '当前版本号',
    `source_type`         varchar(16)      NOT NULL DEFAULT 'APP'   COMMENT '来源（APP/COLLECT/IMPORT）',
    `source_code`         varchar(32)        DEFAULT NULL           COMMENT '采集源标识',
    `source_id`           varchar(64)        DEFAULT NULL           COMMENT '采集源侧编号',
    `source_url`          varchar(500)       DEFAULT NULL           COMMENT '来源页面链接（可追溯）',
    `fetch_time`          datetime           DEFAULT NULL           COMMENT '来源抓取时间',
    `dedupe_key`          varchar(64)        DEFAULT NULL           COMMENT '去重指纹',
    `agreement_version`   varchar(16)        DEFAULT NULL           COMMENT '合规告知版本号',
    `agreement_accepted`  bit(1)       NOT NULL DEFAULT b'0'        COMMENT '是否已勾选合规告知（真实承诺）',
    `agreement_time`      datetime           DEFAULT NULL           COMMENT '合规告知确认时间',
    `lead_count`          int              NOT NULL DEFAULT 0       COMMENT '线索数（冗余）',
    `view_count`          int              NOT NULL DEFAULT 0       COMMENT '浏览数（冗余）',
    `favorite_count`      int              NOT NULL DEFAULT 0       COMMENT '收藏数（冗余）',
    `report_count`        int              NOT NULL DEFAULT 0       COMMENT '被举报次数（冗余）',
    `create_user`         bigint(20)         DEFAULT NULL           COMMENT '创建人',
    `create_time`         datetime         NOT NULL                COMMENT '创建时间',
    `update_user`         bigint(20)         DEFAULT NULL           COMMENT '修改人',
    `update_time`         datetime           DEFAULT NULL           COMMENT '修改时间',
    `deleted`             bigint(20)       NOT NULL DEFAULT 0       COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_case_no`(`case_no`, `deleted`),
    UNIQUE INDEX `uk_source`(`source_type`, `source_code`, `source_id`, `deleted`),
    INDEX `idx_status_urgency_missing_time`(`status`, `urgency_level`, `missing_time`),
    INDEX `idx_status_publish_time`(`status`, `publish_time`),
    INDEX `idx_region`(`province_code`, `city_code`),
    INDEX `idx_owner`(`owner_id`, `status`),
    INDEX `idx_dedupe_key`(`dedupe_key`),
    INDEX `idx_name`(`name`),
    INDEX `idx_create_time`(`create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻人档案表';

CREATE TABLE IF NOT EXISTS `gf_find_case_media` (
    `id`          bigint(20)   AUTO_INCREMENT             COMMENT 'ID',
    `case_id`     bigint(20)   NOT NULL                   COMMENT '档案 ID',
    `type`        varchar(16)  NOT NULL DEFAULT 'PHOTO'   COMMENT '类型（PHOTO/VIDEO）',
    `url`         varchar(500) NOT NULL                   COMMENT '地址',
    `thumb_url`   varchar(500)   DEFAULT NULL             COMMENT '缩略图地址',
    `storage_id`  bigint(20)     DEFAULT NULL             COMMENT 'sys_file ID',
    `sha256`      varchar(64)    DEFAULT NULL             COMMENT '内容摘要',
    `sort`        int          NOT NULL DEFAULT 0         COMMENT '排序',
    `create_user` bigint(20)     DEFAULT NULL             COMMENT '创建人',
    `create_time` datetime     NOT NULL                   COMMENT '创建时间',
    `update_user` bigint(20)     DEFAULT NULL             COMMENT '修改人',
    `update_time` datetime       DEFAULT NULL             COMMENT '修改时间',
    `deleted`     bigint(20)   NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_case_sort`(`case_id`, `sort`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻人档案媒体表';

CREATE TABLE IF NOT EXISTS `gf_find_case_alias` (
    `id`          bigint(20)  AUTO_INCREMENT               COMMENT 'ID',
    `case_id`     bigint(20)  NOT NULL                     COMMENT '档案 ID',
    `name`        varchar(64) NOT NULL                     COMMENT '别名',
    `kind`        varchar(16) NOT NULL DEFAULT 'NICKNAME'  COMMENT '类别（NICKNAME：小名；ACCENT：口音叫法；OTHER：其他）',
    `create_user` bigint(20)    DEFAULT NULL               COMMENT '创建人',
    `create_time` datetime    NOT NULL                     COMMENT '创建时间',
    `update_user` bigint(20)    DEFAULT NULL               COMMENT '修改人',
    `update_time` datetime      DEFAULT NULL               COMMENT '修改时间',
    `deleted`     bigint(20)  NOT NULL DEFAULT 0           COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_case_id`(`case_id`),
    INDEX `idx_name`(`name`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻人档案别名表';

CREATE TABLE IF NOT EXISTS `gf_case_version` (
    `id`            bigint(20) AUTO_INCREMENT              COMMENT 'ID',
    `case_id`       bigint(20) NOT NULL                    COMMENT '档案 ID',
    `seq`           int        NOT NULL                    COMMENT '版本序号',
    `snapshot_json` longtext   NOT NULL                    COMMENT '提交时全量快照',
    `reason`        varchar(255) DEFAULT NULL              COMMENT '提交/变更说明',
    `operator_id`   bigint(20)   DEFAULT NULL              COMMENT '操作人（App 用户或后台用户）',
    `operator_type` varchar(16) NOT NULL DEFAULT 'APP'      COMMENT '操作人类型（APP/SYS）',
    `create_user`   bigint(20)   DEFAULT NULL              COMMENT '创建人',
    `create_time`   datetime   NOT NULL                    COMMENT '创建时间',
    `update_user`   bigint(20)   DEFAULT NULL              COMMENT '修改人',
    `update_time`   datetime     DEFAULT NULL              COMMENT '修改时间',
    `deleted`       bigint(20) NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_case_seq`(`case_id`, `seq`, `deleted`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻人档案版本表';

CREATE TABLE IF NOT EXISTS `gf_case_status_log` (
    `id`            bigint(20) AUTO_INCREMENT              COMMENT 'ID',
    `case_id`       bigint(20) NOT NULL                    COMMENT '档案 ID',
    `from_status`   varchar(20)  DEFAULT NULL              COMMENT '变更前状态',
    `to_status`     varchar(20) NOT NULL                   COMMENT '变更后状态',
    `reason_code`   varchar(32)  DEFAULT NULL              COMMENT '理由码（审核模板）',
    `reason`        varchar(500) DEFAULT NULL              COMMENT '理由说明',
    `operator_id`   bigint(20)   DEFAULT NULL              COMMENT '操作人',
    `operator_type` varchar(16) NOT NULL DEFAULT 'APP'     COMMENT '操作人类型（APP/SYS/SYSTEM）',
    `create_time`   datetime    NOT NULL                   COMMENT '创建时间',
    `create_user`   bigint(20)     DEFAULT NULL             COMMENT '创建人',
    `update_user`   bigint(20)     DEFAULT NULL             COMMENT '修改人',
    `update_time`   datetime       DEFAULT NULL             COMMENT '修改时间',
    `deleted`       bigint(20)  NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_case_time`(`case_id`, `create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻人档案状态轨迹表';

CREATE TABLE IF NOT EXISTS `gf_case_source_link` (
    `id`           bigint(20) AUTO_INCREMENT                COMMENT 'ID',
    `case_id`      bigint(20) NOT NULL                     COMMENT '档案 ID',
    `candidate_id` bigint(20)   DEFAULT NULL               COMMENT '采集候选 ID',
    `source_code`  varchar(32)  DEFAULT NULL               COMMENT '采集源标识',
    `source_id`    varchar(64)  DEFAULT NULL               COMMENT '源侧编号',
    `source_url`   varchar(500) DEFAULT NULL               COMMENT '源页面链接',
    `fetch_time`   datetime       DEFAULT NULL             COMMENT '抓取时间',
    `link_type`    varchar(16) NOT NULL DEFAULT 'APPROVE'  COMMENT '关联方式（APPROVE：审批生成；MERGE：合并入既有档案）',
    `create_user`  bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`  datetime    NOT NULL                    COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`      bigint(20)  NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_case_id`(`case_id`),
    INDEX `idx_candidate_id`(`candidate_id`),
    UNIQUE INDEX `uk_source_ref`(`source_code`, `source_id`, `deleted`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻人档案来源关联表';

-- ----------------------------
-- 线索
-- ----------------------------
CREATE TABLE IF NOT EXISTS `gf_case_lead` (
    `id`             bigint(20) AUTO_INCREMENT               COMMENT 'ID',
    `case_id`        bigint(20) NOT NULL                    COMMENT '档案 ID',
    `submitter_id`   bigint(20) NOT NULL                    COMMENT '提交人（App 用户 ID）',
    `occur_time`     datetime       DEFAULT NULL            COMMENT '发现时间',
    `occur_address`  varchar(255)   DEFAULT NULL            COMMENT '发现地点描述',
    `description`    varchar(1000) NOT NULL                 COMMENT '线索描述',
    `media_urls`     varchar(1500)  DEFAULT NULL            COMMENT '图片地址（JSON 数组）',
    `contact_mode`   varchar(16) NOT NULL DEFAULT 'IN_APP'  COMMENT '联络方式（IN_APP/PHONE）',
    `contact_value`  varchar(255)   DEFAULT NULL            COMMENT '联系方式（加密存储）',
    `status`         varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/ACCEPTED/REJECTED/CONTACTED）',
    `high_risk`      bit(1)      NOT NULL DEFAULT b'0'      COMMENT '是否高风险（命中诈骗话术）',
    `handle_remark`  varchar(500)   DEFAULT NULL            COMMENT '处置说明',
    `ip`             varchar(64)    DEFAULT NULL            COMMENT '提交 IP（调证用）',
    `create_user`    bigint(20)     DEFAULT NULL            COMMENT '创建人',
    `create_time`    datetime    NOT NULL                   COMMENT '创建时间',
    `update_user`    bigint(20)     DEFAULT NULL            COMMENT '修改人',
    `update_time`    datetime       DEFAULT NULL            COMMENT '修改时间',
    `deleted`        bigint(20)  NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_case_time`(`case_id`, `create_time`),
    INDEX `idx_submitter`(`submitter_id`, `create_time`),
    INDEX `idx_status_risk`(`status`, `high_risk`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寻人线索表';

CREATE TABLE IF NOT EXISTS `gf_lead_reply` (
    `id`          bigint(20) AUTO_INCREMENT              COMMENT 'ID',
    `lead_id`     bigint(20) NOT NULL                    COMMENT '线索 ID',
    `from_side`   varchar(16) NOT NULL                   COMMENT '发起方（OWNER/SUBMITTER）',
    `from_user_id` bigint(20) NOT NULL                   COMMENT '发起人',
    `content`     varchar(500) NOT NULL                  COMMENT '内容',
    `create_user` bigint(20)   DEFAULT NULL              COMMENT '创建人',
    `create_time` datetime   NOT NULL                    COMMENT '创建时间',
    `update_user` bigint(20)   DEFAULT NULL              COMMENT '修改人',
    `update_time` datetime     DEFAULT NULL              COMMENT '修改时间',
    `deleted`     bigint(20) NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_lead_time`(`lead_id`, `create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线索回复表';

-- ----------------------------
-- C 端消息与偏好
-- ----------------------------
CREATE TABLE IF NOT EXISTS `gf_message` (
    `id`           bigint(20) AUTO_INCREMENT               COMMENT 'ID',
    `receiver_id`  bigint(20) NOT NULL                    COMMENT '接收人（App 用户 ID）',
    `type`         varchar(24) NOT NULL                   COMMENT '事件类型（NotificationEventEnum）',
    `category`     varchar(16) NOT NULL DEFAULT 'SYSTEM'   COMMENT '分组（AUDIT/LEAD/RESULT/SYSTEM）',
    `title`        varchar(64) NOT NULL                    COMMENT '标题',
    `content`      varchar(500) NOT NULL                   COMMENT '内容',
    `biz_type`     varchar(24)  DEFAULT NULL               COMMENT '业务类型（FIND_CASE/LEAD/REPORT/COLLECT）',
    `biz_id`       bigint(20)   DEFAULT NULL               COMMENT '业务 ID',
    `path`         varchar(255) DEFAULT NULL               COMMENT '跳转路径',
    `read_status`  bit(1)      NOT NULL DEFAULT b'0'       COMMENT '是否已读',
    `read_time`    datetime     DEFAULT NULL               COMMENT '阅读时间',
    `create_user`  bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`  datetime   NOT NULL                     COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`      bigint(20)  NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_receiver_read`(`receiver_id`, `read_status`, `create_time`),
    INDEX `idx_receiver_category`(`receiver_id`, `category`),
    INDEX `idx_biz`(`biz_type`, `biz_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 站内消息表';

CREATE TABLE IF NOT EXISTS `gf_notification_preference` (
    `id`            bigint(20) AUTO_INCREMENT              COMMENT 'ID',
    `user_id`       bigint(20) NOT NULL                    COMMENT 'App 用户 ID',
    `type`          varchar(24) NOT NULL                   COMMENT '事件类型',
    `inbox_enabled` bit(1) NOT NULL DEFAULT b'1'           COMMENT '是否开启站内信',
    `sms_enabled`   bit(1) NOT NULL DEFAULT b'0'           COMMENT '是否开启短信提醒',
    `create_user`   bigint(20)   DEFAULT NULL              COMMENT '创建人',
    `create_time`   datetime   NOT NULL                    COMMENT '创建时间',
    `update_user`   bigint(20)   DEFAULT NULL              COMMENT '修改人',
    `update_time`   datetime     DEFAULT NULL              COMMENT '修改时间',
    `deleted`       bigint(20)  NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_type`(`user_id`, `type`, `deleted`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 通知偏好表';

-- ----------------------------
-- 内容治理
-- ----------------------------
CREATE TABLE IF NOT EXISTS `gf_report` (
    `id`           bigint(20) AUTO_INCREMENT                 COMMENT 'ID',
    `reporter_id`  bigint(20) NOT NULL                       COMMENT '举报人',
    `target_type`  varchar(16) NOT NULL                     COMMENT '目标类型（CASE/LEAD/USER）',
    `target_id`    bigint(20) NOT NULL                       COMMENT '目标 ID',
    `reason_type`  varchar(24) NOT NULL                     COMMENT '举报理由（字典 report_reason）',
    `description`  varchar(500)  DEFAULT NULL               COMMENT '补充描述',
    `media_urls`   varchar(1500) DEFAULT NULL                COMMENT '证据图片（JSON 数组）',
    `status`       varchar(16) NOT NULL DEFAULT 'PENDING'    COMMENT '状态（PENDING/APPROVED/DISMISSED）',
    `hit_count`    int       NOT NULL DEFAULT 1              COMMENT  '相同理由举报次数',
    `handle_remark` varchar(500) DEFAULT NULL                COMMENT '处理说明',
    `handler_id`   bigint(20)    DEFAULT NULL                COMMENT '处理人（后台用户）',
    `handle_time`  datetime      DEFAULT NULL                COMMENT '处理时间',
    `create_user`  bigint(20)    DEFAULT NULL                COMMENT '创建人',
    `create_time`  datetime    NOT NULL                      COMMENT '创建时间',
    `update_user`  bigint(20)    DEFAULT NULL                COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`      bigint(20)  NOT NULL DEFAULT 0            COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_reporter_target`(`reporter_id`, `target_type`, `target_id`, `deleted`),
    INDEX `idx_target_status`(`target_type`, `target_id`, `status`),
    INDEX `idx_status_time`(`status`, `create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

CREATE TABLE IF NOT EXISTS `gf_sensitive_word` (
    `id`          bigint(20)  AUTO_INCREMENT                COMMENT 'ID',
    `word`        varchar(64) NOT NULL                      COMMENT '敏感词',
    `category`    varchar(16) NOT NULL                      COMMENT '类别（ILLEGAL/ABUSE/FRAUD）',
    `status`      tinyint(1) UNSIGNED NOT NULL DEFAULT 1     COMMENT '状态（1：启用；2：停用）',
    `remark`      varchar(200) DEFAULT NULL                 COMMENT '备注',
    `create_user` bigint(20)    DEFAULT NULL                COMMENT '创建人',
    `create_time` datetime    NOT NULL                      COMMENT '创建时间',
    `update_user` bigint(20)    DEFAULT NULL                COMMENT '修改人',
    `update_time` datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`     bigint(20)  NOT NULL DEFAULT 0            COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_word`(`word`, `deleted`),
    INDEX `idx_category`(`category`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词表';

CREATE TABLE IF NOT EXISTS `gf_user_punishment` (
    `id`          bigint(20)  AUTO_INCREMENT                 COMMENT 'ID',
    `user_id`     bigint(20)  NOT NULL                       COMMENT '被处置用户',
    `type`        varchar(16) NOT NULL                        COMMENT '处置类型（MUTE/BAN）',
    `days`        int           DEFAULT NULL                  COMMENT '时长（天，空为永久）',
    `start_time`  datetime    NOT NULL                        COMMENT '开始时间',
    `end_time`    datetime      DEFAULT NULL                  COMMENT '结束时间',
    `reason`      varchar(500)  DEFAULT NULL                  COMMENT '原因',
    `report_id`   bigint(20)    DEFAULT NULL                  COMMENT '关联举报 ID',
    `create_user` bigint(20)    DEFAULT NULL                  COMMENT '创建人',
    `create_time` datetime    NOT NULL                        COMMENT '创建时间',
    `update_user` bigint(20)    DEFAULT NULL                  COMMENT '修改人',
    `update_time` datetime      DEFAULT NULL                  COMMENT '修改时间',
    `deleted`     bigint(20)  NOT NULL DEFAULT 0              COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_type`(`user_id`, `type`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户处置记录表';

CREATE TABLE IF NOT EXISTS `gf_favorite` (
    `id`          bigint(20) AUTO_INCREMENT                  COMMENT 'ID',
    `user_id`     bigint(20) NOT NULL                        COMMENT 'App 用户 ID',
    `target_type` varchar(16) NOT NULL DEFAULT 'CASE'        COMMENT '目标类型',
    `target_id`   bigint(20) NOT NULL                        COMMENT '目标 ID',
    `create_user` bigint(20)   DEFAULT NULL                  COMMENT '创建人',
    `create_time` datetime   NOT NULL                        COMMENT '创建时间',
    `update_user` bigint(20)   DEFAULT NULL                  COMMENT '修改人',
    `update_time` datetime     DEFAULT NULL                  COMMENT '修改时间',
    `deleted`     bigint(20)  NOT NULL DEFAULT 0             COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_target`(`user_id`, `target_type`, `target_id`, `deleted`),
    INDEX `idx_user_time`(`user_id`, `create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS `gf_search_history` (
    `id`          bigint(20)  AUTO_INCREMENT                 COMMENT 'ID',
    `user_id`     bigint(20)  NOT NULL                       COMMENT 'App 用户 ID',
    `keyword`     varchar(64) NOT NULL                       COMMENT '搜索词',
    `create_user` bigint(20)    DEFAULT NULL                 COMMENT '创建人',
    `create_time` datetime    NOT NULL                       COMMENT '创建时间',
    `update_user` bigint(20)    DEFAULT NULL                 COMMENT '修改人',
    `update_time` datetime      DEFAULT NULL                 COMMENT '修改时间',
    `deleted`     bigint(20)  NOT NULL DEFAULT 0             COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_time`(`user_id`, `create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

CREATE TABLE IF NOT EXISTS `gf_user_track` (
    `id`           bigint(20) AUTO_INCREMENT                 COMMENT 'ID',
    `user_id`      bigint(20)   DEFAULT NULL                  COMMENT 'App 用户 ID（可匿名）',
    `event_type`   varchar(16) NOT NULL                       COMMENT '事件（EXPOSE/CLICK/SHARE/PUBLISH）',
    `target_type`  varchar(16)  DEFAULT NULL                  COMMENT '目标类型',
    `target_id`    bigint(20)   DEFAULT NULL                  COMMENT '目标 ID',
    `extra`        varchar(1000) DEFAULT NULL                 COMMENT '扩展信息（JSON）',
    `create_user`  bigint(20)   DEFAULT NULL                  COMMENT '创建人',
    `create_time`  datetime    NOT NULL                       COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL                  COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL                  COMMENT '修改时间',
    `deleted`      bigint(20)  NOT NULL DEFAULT 0             COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_event_time`(`event_type`, `create_time`),
    INDEX `idx_target`(`target_type`, `target_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='埋点事件表';

-- ----------------------------
-- 数据采集
-- ----------------------------
CREATE TABLE IF NOT EXISTS `gf_collect_source` (
    `id`             bigint(20)  AUTO_INCREMENT                COMMENT 'ID',
    `code`           varchar(32) NOT NULL                      COMMENT '源标识',
    `name`           varchar(64) NOT NULL                      COMMENT '源名称',
    `base_url`       varchar(255) NOT NULL                     COMMENT '站点根地址',
    `list_path_tpl`  varchar(255)  DEFAULT NULL                COMMENT '列表路径模板',
    `detail_path_tpl` varchar(255) DEFAULT NULL                COMMENT '详情路径模板',
    `category_ids`   varchar(255)  DEFAULT NULL                COMMENT '栏目/分类标识（JSON 数组）',
    `crawl_delay_ms` int           DEFAULT 2000                COMMENT '同域抓取间隔（毫秒），不得低于站点声明值',
    `max_pages`      int           NOT NULL DEFAULT 5          COMMENT '单轮最大列表页数',
    `request_timeout_ms` int        DEFAULT 10000              COMMENT '请求超时（毫秒）',
    `cron_expression` varchar(64)  DEFAULT NULL                COMMENT '调度表达式',
    `enabled`        bit(1) NOT NULL DEFAULT b'0'              COMMENT '是否启用（默认关闭，须书面授权后开启）',
    `authorization_note` varchar(500) DEFAULT NULL             COMMENT '授权依据说明',
    `robots_allowed` bit(1) NOT NULL DEFAULT b'0'              COMMENT '最近一次 robots 判定结果',
    `last_cursor`    varchar(255)  DEFAULT NULL                COMMENT '上次采集游标（页码等）',
    `last_run_time`  datetime      DEFAULT NULL                COMMENT '上次执行时间',
    `remark`         varchar(500)  DEFAULT NULL                COMMENT '备注',
    `create_user`    bigint(20)    DEFAULT NULL                COMMENT '创建人',
    `create_time`    datetime    NOT NULL                      COMMENT '创建时间',
    `update_user`    bigint(20)    DEFAULT NULL                COMMENT '修改人',
    `update_time`    datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`        bigint(20)  NOT NULL DEFAULT 0            COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_code`(`code`, `deleted`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集源配置表';

CREATE TABLE IF NOT EXISTS `gf_collect_raw` (
    `id`           bigint(20)  AUTO_INCREMENT                  COMMENT 'ID',
    `source_code`  varchar(32) NOT NULL                        COMMENT '采集源标识',
    `source_id`    varchar(64) NOT NULL                        COMMENT '源侧编号',
    `source_url`   varchar(500) NOT NULL                       COMMENT '来源页面链接',
    `page_type`    varchar(16) NOT NULL DEFAULT 'DETAIL'       COMMENT '页面类型（LIST/DETAIL）',
    `content`      longtext        DEFAULT NULL                COMMENT '原文正文（180 天后清理）',
    `sha256`       varchar(64) NOT NULL                        COMMENT '原文摘要',
    `fetched_at`   datetime    NOT NULL                        COMMENT '抓取时间',
    `erased`       bit(1) NOT NULL DEFAULT b'0'                COMMENT '正文是否已按留存策略清理',
    `create_user`  bigint(20)    DEFAULT NULL                  COMMENT '创建人',
    `create_time`  datetime    NOT NULL                        COMMENT '创建时间',
    `update_user`  bigint(20)    DEFAULT NULL                  COMMENT '修改人',
    `update_time`  datetime      DEFAULT NULL                  COMMENT '修改时间',
    `deleted`      bigint(20)  NOT NULL DEFAULT 0              COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_source_id`(`source_code`, `source_id`, `deleted`),
    INDEX `idx_content_expire`(`erased`, `create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集原文快照表';

CREATE TABLE IF NOT EXISTS `gf_collect_candidate` (
    `id`               bigint(20)  AUTO_INCREMENT               COMMENT 'ID',
    `source_code`      varchar(32) NOT NULL                     COMMENT '采集源标识',
    `source_id`        varchar(64) NOT NULL                     COMMENT '源侧编号',
    `source_url`       varchar(500) NOT NULL                    COMMENT '来源页面链接',
    `fetch_time`       datetime     NOT NULL                    COMMENT '抓取时间',
    `raw_id`           bigint(20)   NOT NULL                    COMMENT '原文快照 ID',
    `category_text`    varchar(64)  DEFAULT NULL                COMMENT '源侧类别原文',
    `name`             varchar(64)    DEFAULT NULL              COMMENT '姓名',
    `aliases`          varchar(500)   DEFAULT NULL              COMMENT '别名（JSON 数组）',
    `gender`           tinyint(1) UNSIGNED DEFAULT NULL         COMMENT '性别（0：未知；1：男；2：女）',
    `birthday`         date           DEFAULT NULL              COMMENT '出生日期',
    `age_at_missing`   int            DEFAULT NULL              COMMENT '失踪时年龄',
    `missing_time`     datetime       DEFAULT NULL              COMMENT '失踪时间',
    `province_code`    varchar(12)    DEFAULT NULL              COMMENT '失踪省编码',
    `city_code`        varchar(12)    DEFAULT NULL              COMMENT '失踪市编码',
    `district_code`    varchar(12)    DEFAULT NULL              COMMENT '失踪区县编码',
    `region_text`      varchar(128)   DEFAULT NULL              COMMENT '区划原文',
    `address`          varchar(500)   DEFAULT NULL              COMMENT '失踪地址原文',
    `height`           int            DEFAULT NULL              COMMENT '失踪时身高',
    `features`         varchar(500)   DEFAULT NULL              COMMENT '特征描述',
    `narrative`        text           DEFAULT NULL              COMMENT '发生经过',
    `family_clues`     text           DEFAULT NULL              COMMENT '家庭背景及线索资料',
    `other_note`       varchar(1000)  DEFAULT NULL              COMMENT '其他说明',
    `contact_text`     varchar(255)   DEFAULT NULL              COMMENT '源页面公开的寻亲联系人原文',
    `cover_url`        varchar(500)   DEFAULT NULL              COMMENT '照片地址（审批时本地化）',
    `case_type`        varchar(20)    DEFAULT 'MISSING'          COMMENT '归一化后的失踪情形',
    `blood_sample_status` varchar(20) DEFAULT 'UNKNOWN'          COMMENT '血样采集情况',
    `dedupe_key`       varchar(64)  NOT NULL                    COMMENT '去重指纹',
    `completeness`     decimal(4, 2) NOT NULL DEFAULT 0         COMMENT '字段完整度（0-1）',
    `status`           varchar(16)  NOT NULL DEFAULT 'PENDING'  COMMENT '状态（PENDING/APPROVED/REJECTED/MERGED/DUPLICATED/ERASED）',
    `primary_id`       bigint(20)     DEFAULT NULL              COMMENT '主候选 ID（DUPLICATED 时指向）',
    `case_id`          bigint(20)     DEFAULT NULL              COMMENT '审批/合并产生的档案 ID',
    `reject_reason`    varchar(500)   DEFAULT NULL              COMMENT '驳回理由',
    `batch_no`         varchar(32)    DEFAULT NULL              COMMENT '批次号',
    `create_user`      bigint(20)     DEFAULT NULL              COMMENT '创建人',
    `create_time`      datetime     NOT NULL                    COMMENT '创建时间',
    `update_user`      bigint(20)     DEFAULT NULL              COMMENT '修改人',
    `update_time`      datetime       DEFAULT NULL              COMMENT '修改时间',
    `deleted`          bigint(20)   NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_source_id`(`source_code`, `source_id`, `deleted`),
    INDEX `idx_dedupe_key`(`dedupe_key`),
    INDEX `idx_status_batch`(`status`, `batch_no`),
    INDEX `idx_region`(`province_code`, `city_code`),
    INDEX `idx_name`(`name`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集候选表';

CREATE TABLE IF NOT EXISTS `gf_collect_log` (
    `id`             bigint(20)  AUTO_INCREMENT                 COMMENT 'ID',
    `batch_no`       varchar(32) NOT NULL                       COMMENT '批次号',
    `source_code`    varchar(32) NOT NULL                       COMMENT '采集源标识',
    `trigger_type`   varchar(16) NOT NULL DEFAULT 'JOB'         COMMENT '触发方式（JOB/MANUAL）',
    `pages`          int         NOT NULL DEFAULT 0             COMMENT '抓取列表页数',
    `fetched`        int         NOT NULL DEFAULT 0             COMMENT '抓取详情数',
    `added`          int         NOT NULL DEFAULT 0             COMMENT '新增候选数',
    `duplicated`     int         NOT NULL DEFAULT 0             COMMENT '重复数',
    `failed`         int         NOT NULL DEFAULT 0             COMMENT '失败数',
    `skipped_reason` varchar(255)   DEFAULT NULL               COMMENT '跳过/中止原因（如 robots 拒绝）',
    `cost_ms`        bigint      NOT NULL DEFAULT 0             COMMENT '耗时（毫秒）',
    `min_interval_ms` int          DEFAULT NULL                 COMMENT '实测最小同域间隔（毫秒）',
    `error_summary`  varchar(1000)  DEFAULT NULL                COMMENT '错误摘要',
    `success`        bit(1)      NOT NULL DEFAULT b'1'          COMMENT '是否成功',
    `create_user`    bigint(20)     DEFAULT NULL                COMMENT '创建人',
    `create_time`    datetime    NOT NULL                       COMMENT '创建时间',
    `update_user`    bigint(20)     DEFAULT NULL                COMMENT '修改人',
    `update_time`    datetime       DEFAULT NULL                COMMENT '修改时间',
    `deleted`        bigint(20)  NOT NULL DEFAULT 0             COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_batch_no`(`batch_no`, `deleted`),
    INDEX `idx_source_time`(`source_code`, `create_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集批次日志表';

CREATE TABLE IF NOT EXISTS `gf_collect_erase_log` (
    `id`            bigint(20)  AUTO_INCREMENT                  COMMENT 'ID',
    `source_code`   varchar(32)    DEFAULT NULL                 COMMENT '采集源标识',
    `source_ids`    varchar(1000)  DEFAULT NULL                 COMMENT '来源编号列表（JSON 数组）',
    `source_urls`   text           DEFAULT NULL                 COMMENT '来源链接列表（JSON 数组）',
    `requester`     varchar(128) NOT NULL                       COMMENT '请求方',
    `remark`        varchar(500)   DEFAULT NULL                 COMMENT '说明',
    `candidate_ids` varchar(1000)  DEFAULT NULL                 COMMENT '受影响候选 ID（JSON 数组）',
    `case_ids`      varchar(1000)  DEFAULT NULL                 COMMENT '受影响档案 ID（JSON 数组）',
    `result`        varchar(255)   DEFAULT NULL                 COMMENT '执行结果',
    `create_user`   bigint(20)     DEFAULT NULL                 COMMENT '创建人',
    `create_time`   datetime    NOT NULL                        COMMENT '创建时间',
    `update_user`   bigint(20)     DEFAULT NULL                 COMMENT '修改人',
    `update_time`   datetime       DEFAULT NULL                 COMMENT '修改时间',
    `deleted`       bigint(20)  NOT NULL DEFAULT 0              COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_source_code`(`source_code`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集数据删除审计表';

-- changeset guardfind:2
CREATE TABLE IF NOT EXISTS `gf_ledger_record` (
    `id`            bigint(20)    AUTO_INCREMENT              COMMENT 'ID',
    `user_id`       bigint(20)    NOT NULL                    COMMENT '所属用户 ID',
    `type`          tinyint(4)    NOT NULL                    COMMENT '类型（1：支出；2：收入）',
    `amount`        decimal(12, 2) NOT NULL                   COMMENT '金额（元，正数）',
    `category`      varchar(20)   NOT NULL                    COMMENT '分类码（food/transport/shopping/housing/entertainment/medical/education/social/salary/investment/redpacket/other）',
    `record_date`   date          NOT NULL                    COMMENT '记账日期',
    `remark`        varchar(200)  DEFAULT NULL                COMMENT '备注',
    `create_user`   bigint(20)    DEFAULT NULL                COMMENT '创建人',
    `create_time`   datetime      NOT NULL                    COMMENT '创建时间',
    `update_user`   bigint(20)    DEFAULT NULL                COMMENT '修改人',
    `update_time`   datetime      DEFAULT NULL                COMMENT '修改时间',
    `deleted`       bigint(20)    NOT NULL DEFAULT 0          COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_date`(`user_id`, `record_date`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账记录表';

-- changeset guardfind:3
CREATE TABLE IF NOT EXISTS `gf_ai_conversation` (
    `id`            bigint(20)   AUTO_INCREMENT             COMMENT 'ID',
    `user_id`       bigint(20)   NOT NULL                   COMMENT '所属用户 ID',
    `title`         varchar(100) NOT NULL                   COMMENT '会话标题（默认取首条消息摘要）',
    `model`         varchar(50)  DEFAULT NULL               COMMENT '对话模型标识（服务端配置的默认模型）',
    `create_user`   bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`   datetime     NOT NULL                   COMMENT '创建时间',
    `update_user`   bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`   datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`       bigint(20)   NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_time`(`user_id`, `update_time`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话表';

-- changeset guardfind:4
CREATE TABLE IF NOT EXISTS `gf_ai_message` (
    `id`              bigint(20) AUTO_INCREMENT             COMMENT 'ID',
    `conversation_id` bigint(20) NOT NULL                   COMMENT '所属会话 ID',
    `user_id`         bigint(20) NOT NULL                   COMMENT '所属用户 ID（冗余，便于按人清理）',
    `role`            varchar(20) NOT NULL                  COMMENT '角色（user/assistant）',
    `content`         text         NOT NULL                 COMMENT '消息内容',
    `tokens`          int(11)      DEFAULT NULL             COMMENT '消耗 token 数（供应商返回时记录）',
    `create_user`     bigint(20)   DEFAULT NULL             COMMENT '创建人',
    `create_time`     datetime     NOT NULL                 COMMENT '创建时间',
    `update_user`     bigint(20)   DEFAULT NULL             COMMENT '修改人',
    `update_time`     datetime     DEFAULT NULL             COMMENT '修改时间',
    `deleted`         bigint(20)   NOT NULL DEFAULT 0       COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_conversation`(`conversation_id`, `id`),
    INDEX `idx_user`(`user_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 消息表';

-- changeset guardfind:5
CREATE TABLE IF NOT EXISTS `gf_tool_countdown` (
    `id`           bigint(20)   AUTO_INCREMENT             COMMENT 'ID',
    `user_id`      bigint(20)   NOT NULL                   COMMENT '所属用户 ID',
    `title`        varchar(50)  NOT NULL                   COMMENT '事项名称',
    `target_date`  date         NOT NULL                   COMMENT '目标日期',
    `remark`       varchar(200) DEFAULT NULL               COMMENT '备注',
    `create_user`  bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`  datetime     NOT NULL                   COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`      bigint(20)   NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_date`(`user_id`, `target_date`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='倒数日表';

-- changeset guardfind:6
CREATE TABLE IF NOT EXISTS `gf_tool_todo` (
    `id`           bigint(20)   AUTO_INCREMENT             COMMENT 'ID',
    `user_id`      bigint(20)   NOT NULL                   COMMENT '所属用户 ID',
    `title`        varchar(100) NOT NULL                   COMMENT '待办内容',
    `done`         tinyint(4)   NOT NULL DEFAULT 0         COMMENT '是否完成（0：否；1：是）',
    `due_date`     date         DEFAULT NULL               COMMENT '截止日期（可空）',
    `create_user`  bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`  datetime     NOT NULL                   COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`      bigint(20)   NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_done`(`user_id`, `done`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办清单表';

-- changeset guardfind:7
CREATE TABLE IF NOT EXISTS `gf_tool_habit` (
    `id`           bigint(20)   AUTO_INCREMENT             COMMENT 'ID',
    `user_id`      bigint(20)   NOT NULL                   COMMENT '所属用户 ID',
    `title`        varchar(50)  NOT NULL                   COMMENT '习惯名称',
    `remark`       varchar(200) DEFAULT NULL               COMMENT '备注',
    `create_user`  bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`  datetime     NOT NULL                   COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`      bigint(20)   NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user`(`user_id`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='习惯打卡表';

-- changeset guardfind:8
CREATE TABLE IF NOT EXISTS `gf_tool_habit_log` (
    `id`           bigint(20)   AUTO_INCREMENT             COMMENT 'ID',
    `habit_id`     bigint(20)   NOT NULL                   COMMENT '所属习惯 ID',
    `user_id`      bigint(20)   NOT NULL                   COMMENT '所属用户 ID（冗余）',
    `checkin_date` date         NOT NULL                   COMMENT '打卡日期',
    `create_user`  bigint(20)   DEFAULT NULL               COMMENT '创建人',
    `create_time`  datetime     NOT NULL                   COMMENT '创建时间',
    `update_user`  bigint(20)   DEFAULT NULL               COMMENT '修改人',
    `update_time`  datetime     DEFAULT NULL               COMMENT '修改时间',
    `deleted`      bigint(20)   NOT NULL DEFAULT 0         COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_habit_date`(`habit_id`, `checkin_date`, `deleted`),
    INDEX `idx_user_date`(`user_id`, `checkin_date`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='习惯打卡流水表';

-- changeset guardfind:9
CREATE TABLE IF NOT EXISTS `gf_tool_split` (
    `id`            bigint(20)     AUTO_INCREMENT           COMMENT 'ID',
    `user_id`       bigint(20)     NOT NULL                 COMMENT '所属用户 ID',
    `title`         varchar(50)    DEFAULT NULL             COMMENT '分账事由（如 聚餐）',
    `total_amount`  decimal(12, 2) NOT NULL                 COMMENT '总金额（元）',
    `people_count`  int(11)        NOT NULL                 COMMENT '参与人数',
    `split_date`    date           NOT NULL                 COMMENT '分账日期',
    `remark`        varchar(200)   DEFAULT NULL             COMMENT '备注',
    `written_back`  tinyint(4)     NOT NULL DEFAULT 0       COMMENT '是否已写回记账（0：否；1：是）',
    `create_user`   bigint(20)     DEFAULT NULL             COMMENT '创建人',
    `create_time`   datetime       NOT NULL                 COMMENT '创建时间',
    `update_user`   bigint(20)     DEFAULT NULL             COMMENT '修改人',
    `update_time`   datetime       DEFAULT NULL             COMMENT '修改时间',
    `deleted`       bigint(20)     NOT NULL DEFAULT 0       COMMENT '是否已删除（0：否；id：是）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_date`(`user_id`, `split_date`),
    INDEX `idx_deleted`(`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AA 分账记录表';
