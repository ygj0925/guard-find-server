-- liquibase formatted sql

-- changeset guardfind:2
-- comment 天下无拐一期基础数据：App 客户端、业务字典、敏感词、后台菜单
-- ----------------------------
-- App 客户端（Sa-Token 独立登录域使用，token 有效期 30 天）
-- ----------------------------
INSERT INTO `sys_client`
(`id`, `client_id`, `client_type`, `auth_type`, `active_timeout`, `timeout`, `is_concurrent`, `max_login_count`, `status`, `create_user`, `create_time`)
VALUES (100, 'a7d3f1c9b5e24086f1a9c4d7e0b23158', 'APP', '["PHONE", "ACCOUNT"]', 604800, 2592000, b'1', 3, 1, 1, NOW())
ON DUPLICATE KEY UPDATE `client_type` = VALUES(`client_type`), `auth_type` = VALUES(`auth_type`),
                        `active_timeout` = VALUES(`active_timeout`), `timeout` = VALUES(`timeout`),
                        `is_concurrent` = VALUES(`is_concurrent`), `max_login_count` = VALUES(`max_login_count`);

INSERT INTO `sys_dict_item`
(`id`, `label`, `value`, `color`, `sort`, `description`, `status`, `dict_id`, `create_user`, `create_time`)
VALUES (5190, 'App', 'APP', 'orange', 4, '移动端 App（iOS/Android/HarmonyOS）', 1, 2, 1, NOW())
ON DUPLICATE KEY UPDATE `label` = VALUES(`label`);

-- ----------------------------
-- 业务字典
-- ----------------------------
INSERT INTO `sys_dict`
(`id`, `name`, `code`, `description`, `is_system`, `create_user`, `create_time`)
VALUES (5000, '失踪情形', 'gf_case_type', '寻人档案的失踪情形分类', b'0', 1, NOW()),
       (5001, '年龄段', 'gf_age_group', '寻人档案年龄段筛选', b'0', 1, NOW()),
       (5002, '血样采集情况', 'gf_blood_sample_status', '打拐 DNA 库血样采集状态', b'0', 1, NOW()),
       (5003, '与本人关系', 'gf_relation_type', '发布人与失踪人员的关系', b'0', 1, NOW()),
       (5004, '举报理由', 'gf_report_reason', '内容举报的理由枚举', b'0', 1, NOW()),
       (5005, '体型', 'gf_body_type', '失踪时体型', b'0', 1, NOW()),
       (5006, '审核驳回理由', 'gf_reject_reason', '审核驳回模板理由', b'0', 1, NOW()),
       (5007, '处置类型', 'gf_punishment_type', '用户处置类型', b'0', 1, NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`);

INSERT INTO `sys_dict_item`
(`id`, `label`, `value`, `color`, `sort`, `description`, `status`, `dict_id`, `create_user`, `create_time`)
VALUES (5100, '疑似被拐', 'TRAFFICKING', 'red', 1, NULL, 1, 5000, 1, NOW()),
       (5101, '走失', 'LOST', 'orange', 2, NULL, 1, 5000, 1, NOW()),
       (5102, '失联', 'NO_CONTACT', 'gold', 3, NULL, 1, 5000, 1, NOW()),
       (5103, '老人走失', 'LOST_ELDERLY', 'volcano', 4, NULL, 1, 5000, 1, NOW()),
       (5104, '其他', 'MISSING', 'blue', 5, NULL, 1, 5000, 1, NOW()),
       (5110, '0-6 岁', '0_6', 'red', 1, NULL, 1, 5001, 1, NOW()),
       (5111, '7-12 岁', '7_12', 'orange', 2, NULL, 1, 5001, 1, NOW()),
       (5112, '13-17 岁', '13_17', 'gold', 3, NULL, 1, 5001, 1, NOW()),
       (5113, '18-59 岁', '18_59', 'blue', 4, NULL, 1, 5001, 1, NOW()),
       (5114, '60 岁及以上', '60_PLUS', 'geekblue', 5, NULL, 1, 5001, 1, NOW()),
       (5120, '已采集', 'COLLECTED', 'green', 1, '已在全国打拐 DNA 库采集血样', 1, 5002, 1, NOW()),
       (5121, '未采集', 'NOT_COLLECTED', 'red', 2, NULL, 1, 5002, 1, NOW()),
       (5122, '未知', 'UNKNOWN', 'gray', 3, NULL, 1, 5002, 1, NOW()),
       (5130, '父母', 'PARENT', 'blue', 1, NULL, 1, 5003, 1, NOW()),
       (5131, '子女', 'CHILD', 'blue', 2, NULL, 1, 5003, 1, NOW()),
       (5132, '配偶', 'SPOUSE', 'blue', 3, NULL, 1, 5003, 1, NOW()),
       (5133, '其他亲属', 'RELATIVE', 'cyan', 4, NULL, 1, 5003, 1, NOW()),
       (5134, '本人', 'SELF', 'purple', 5, NULL, 1, 5003, 1, NOW()),
       (5135, '志愿者/其他', 'OTHER', 'gray', 6, NULL, 1, 5003, 1, NOW()),
       (5140, '涉嫌诈骗', 'FRAUD', 'red', 1, '借寻亲名义索要钱财、邮费、核实费', 1, 5004, 1, NOW()),
       (5141, '虚假信息', 'FALSE_INFO', 'volcano', 2, NULL, 1, 5004, 1, NOW()),
       (5142, '违法违规', 'ILLEGAL', 'magenta', 3, NULL, 1, 5004, 1, NOW()),
       (5143, '淫秽色情', 'PORN', 'red', 4, NULL, 1, 5004, 1, NOW()),
       (5144, '骚扰辱骂', 'HARASSMENT', 'orange', 5, NULL, 1, 5004, 1, NOW()),
       (5145, '重复发布', 'REPEAT', 'gold', 6, NULL, 1, 5004, 1, NOW()),
       (5146, '其他', 'OTHER', 'gray', 7, NULL, 1, 5004, 1, NOW()),
       (5150, '偏瘦', 'THIN', 'cyan', 1, NULL, 1, 5005, 1, NOW()),
       (5151, '中等', 'MEDIUM', 'blue', 2, NULL, 1, 5005, 1, NOW()),
       (5152, '偏胖', 'HEAVY', 'orange', 3, NULL, 1, 5005, 1, NOW()),
       (5160, '信息不完整', 'INCOMPLETE', 'gold', 1, '关键字段缺失，请补充后重新提交', 1, 5006, 1, NOW()),
       (5161, '照片不清晰', 'PHOTO_UNCLEAR', 'gold', 2, NULL, 1, 5006, 1, NOW()),
       (5162, '疑似重复档案', 'DUPLICATED', 'orange', 3, NULL, 1, 5006, 1, NOW()),
       (5163, '无法核实真实性', 'UNVERIFIABLE', 'volcano', 4, NULL, 1, 5006, 1, NOW()),
       (5164, '内容违规', 'VIOLATION', 'red', 5, NULL, 1, 5006, 1, NOW()),
       (5170, '禁言', 'MUTE', 'orange', 1, NULL, 1, 5007, 1, NOW()),
       (5171, '封禁', 'BAN', 'red', 2, NULL, 1, 5007, 1, NOW());

-- ----------------------------
-- 敏感词初始词库（示例集，运营在后台持续维护）
-- ----------------------------
INSERT INTO `gf_sensitive_word`
(`id`, `word`, `category`, `status`, `remark`, `create_user`, `create_time`)
VALUES (5200, '先转钱', 'FRAUD', 1, '借寻亲索要钱财', 1, NOW()),
       (5201, '交核实费', 'FRAUD', 1, NULL, 1, NOW()),
       (5202, '邮费到付', 'FRAUD', 1, '常见找回诈骗话术', 1, NOW()),
       (5203, '加微信付款', 'FRAUD', 1, NULL, 1, NOW()),
       (5204, '保证金', 'FRAUD', 1, NULL, 1, NOW()),
       (5205, '私下交易', 'FRAUD', 1, NULL, 1, NOW()),
       (5206, '买卖孩子', 'ILLEGAL', 1, NULL, 1, NOW()),
       (5207, '代办出生证明', 'ILLEGAL', 1, NULL, 1, NOW()),
       (5208, '私下送养收费', 'ILLEGAL', 1, NULL, 1, NOW());

-- ----------------------------
-- 采集源初始配置（enabled 默认关闭，须取得站方书面授权后由运营开启）
-- ----------------------------
INSERT INTO `gf_collect_source`
(`id`, `code`, `name`, `base_url`, `list_path_tpl`, `detail_path_tpl`, `category_ids`, `crawl_delay_ms`, `max_pages`,
 `request_timeout_ms`, `cron_expression`, `enabled`, `authorization_note`, `remark`, `create_user`, `create_time`)
VALUES (1, 'rangaihuijia', '公开寻亲库（关爱回家）', 'https://www.rangaihuijia.com',
        '/xunren/category/{categoryId}?p-page={page}', '/xunren/view/{sourceId}', '["6"]', 2000, 5, 10000,
        '0 30 3 * * ?', b'0',
        '待补充：上线前须取得站方书面授权或法务确认，未确认前保持关闭', 'robots.txt 实测为 User-agent: * / Allow: /', 1, NOW()),
       (2, 'local-fixture', '本地样例源（离线测试与演示）', 'local://fixtures',
        NULL, NULL, '["default"]', 0, 1, 0,
        NULL, b'1',
        '仅读取仓库内合成样例，不发起任何外部请求', '用于 CI 与本地演示，不参与生产调度', 1, NOW());

-- ----------------------------
-- 后台菜单与权限（采集管理 / 内容治理）
-- ----------------------------
INSERT INTO `sys_menu`
(`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES (9300, '寻亲治理', 0, 1, '/guardfind', 'GuardFind', 'Layout', '/guardfind/moderation', 'heart', b'0', b'0', b'0', NULL, 9, 1, 1, NOW()),
       (9310, '内容审核', 9300, 2, '/guardfind/moderation', 'GuardFindModeration', 'guardfind/moderation/index', NULL, 'audit', b'0', b'0', b'0', NULL, 1, 1, 1, NOW()),
       (9311, '列表', 9310, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:moderation:list', 1, 1, 1, NOW()),
       (9312, '审核通过', 9310, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:moderation:approve', 2, 1, 1, NOW()),
       (9313, '审核驳回', 9310, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:moderation:reject', 3, 1, 1, NOW()),
       (9320, '举报管理', 9300, 2, '/guardfind/report', 'GuardFindReport', 'guardfind/report/index', NULL, 'warning', b'0', b'0', b'0', NULL, 2, 1, 1, NOW()),
       (9321, '列表', 9320, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:moderation:report:list', 1, 1, 1, NOW()),
       (9322, '处理', 9320, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:moderation:report:handle', 2, 1, 1, NOW()),
       (9330, '采集源', 9300, 2, '/guardfind/collect-source', 'GuardFindCollectSource', 'guardfind/collect/source/index', NULL, 'cloud-download', b'0', b'0', b'0', NULL, 3, 1, 1, NOW()),
       (9331, '列表', 9330, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:source:list', 1, 1, 1, NOW()),
       (9332, '详情', 9330, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:source:get', 2, 1, 1, NOW()),
       (9333, '新增', 9330, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:source:create', 3, 1, 1, NOW()),
       (9334, '修改', 9330, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:source:update', 4, 1, 1, NOW()),
       (9335, '删除', 9330, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:source:delete', 5, 1, 1, NOW()),
       (9336, '执行采集', 9330, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:source:run', 6, 1, 1, NOW()),
       (9340, '采集候选', 9300, 2, '/guardfind/collect-candidate', 'GuardFindCollectCandidate', 'guardfind/collect/candidate/index', NULL, 'database', b'0', b'0', b'0', NULL, 4, 1, 1, NOW()),
       (9341, '列表', 9340, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:candidate:list', 1, 1, 1, NOW()),
       (9342, '详情', 9340, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:candidate:get', 2, 1, 1, NOW()),
       (9343, '审批入库', 9340, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:candidate:approve', 3, 1, 1, NOW()),
       (9344, '合并', 9340, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:candidate:merge', 4, 1, 1, NOW()),
       (9345, '驳回', 9340, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:candidate:reject', 5, 1, 1, NOW()),
       (9346, '删除', 9340, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:candidate:delete', 6, 1, 1, NOW()),
       (9347, '撤回清除', 9340, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:candidate:erase', 7, 1, 1, NOW()),
       (9350, '采集日志', 9300, 2, '/guardfind/collect-log', 'GuardFindCollectLog', 'guardfind/collect/log/index', NULL, 'file-text', b'0', b'0', b'0', NULL, 5, 1, 1, NOW()),
       (9351, '列表', 9350, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'system:collect:log:list', 1, 1, 1, NOW());
