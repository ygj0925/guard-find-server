/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.guardfind.server.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.common.log.SnailJobLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.service.CaseStatusService;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import com.guardfind.server.schedule.annotation.ConditionalOnEnabledScheduleJob;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.core.constant.PropertiesConstants;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 寻人档案时效任务
 *
 * <p>
 * 规则：已发布档案超过 {@link #EXPIRE_DAYS} 天无人确认则自动归档退出广场首屏（可续期）； 到期前 {@link #WARN_DAYS_BEFORE}
 * 天向发布人发送一次临期提醒（Redis 标记去重，避免每天重复打扰）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseExpiryJob {

    /**
     * 时效天数
     */
    private static final int EXPIRE_DAYS = 90;

    /**
     * 提前提醒天数
     */
    private static final int WARN_DAYS_BEFORE = 7;

    /**
     * 临期提醒去重标记键前缀
     */
    private static final String EXPIRING_WARN_KEY_PREFIX = "GF:CASE:EXPIRING:";

    /**
     * 定时归档过期档案（未启用 Snail Job 则使用它）
     */
    @Component
    @ConditionalOnProperty(prefix = "snail-job", name = PropertiesConstants.ENABLED, havingValue = "false")
    public static class Scheduler {

        @TenantIgnore
        @Scheduled(cron = "0 15 3 * * ?")
        public void expireCaseWithSchedule() {
            log.info("定时任务 [寻人档案时效] 开始执行。");
            execute();
            log.info("定时任务 [寻人档案时效] 执行结束。");
        }
    }

    /**
     * 定时归档过期档案（启用 Snail Job 时）
     */
    @Component
    @ConditionalOnEnabledScheduleJob
    public static class ScheduleJob {

        @TenantIgnore
        @JobExecutor(name = "CaseExpiryJob")
        public void expireCaseWithScheduleJob() {
            SnailJobLog.REMOTE.info("定时任务 [寻人档案时效] 开始执行。");
            execute();
            SnailJobLog.REMOTE.info("定时任务 [寻人档案时效] 执行结束。");
        }
    }

    /**
     * 执行时效处理
     */
    private static void execute() {
        FindCaseMapper findCaseMapper = SpringUtil.getBean(FindCaseMapper.class);
        LocalDateTime now = LocalDateTime.now();
        archiveExpired(findCaseMapper, now);
        warnExpiring(findCaseMapper, now);
    }

    /**
     * 超期未确认的档案自动归档
     *
     * @param findCaseMapper 档案 Mapper
     * @param now            当前时间
     */
    private static void archiveExpired(FindCaseMapper findCaseMapper, LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(EXPIRE_DAYS);
        List<FindCaseDO> expiredList = findCaseMapper.lambdaQuery()
            .eq(FindCaseDO::getStatus, CaseStatusEnum.PUBLISHED)
            .apply("(COALESCE(last_confirm_time, publish_time, create_time) < {0})", cutoff)
            .list();
        if (CollUtil.isEmpty(expiredList)) {
            return;
        }
        CaseStatusService caseStatusService = SpringUtil.getBean(CaseStatusService.class);
        for (FindCaseDO caseDO : expiredList) {
            // 状态机内部完成合法性校验、轨迹留存与通知，此处不直接改状态
            caseStatusService
                .change(caseDO, CaseStatusEnum.ARCHIVED, "EXPIRED", "超过 " + EXPIRE_DAYS + " 天无人确认，自动归档", null, CaseOperatorTypeEnum.SYSTEM);
        }
        log.info("本次自动归档档案 {} 条", expiredList.size());
    }

    /**
     * 到期前提醒发布人续期
     *
     * @param findCaseMapper 档案 Mapper
     * @param now            当前时间
     */
    private static void warnExpiring(FindCaseMapper findCaseMapper, LocalDateTime now) {
        LocalDateTime from = now.minusDays(EXPIRE_DAYS - WARN_DAYS_BEFORE);
        LocalDateTime to = now.minusDays(EXPIRE_DAYS - WARN_DAYS_BEFORE - 1);
        List<FindCaseDO> expiringList = findCaseMapper.lambdaQuery()
            .eq(FindCaseDO::getStatus, CaseStatusEnum.PUBLISHED)
            .isNotNull(FindCaseDO::getOwnerId)
            .apply("(COALESCE(last_confirm_time, publish_time, create_time) >= {0} AND COALESCE(last_confirm_time, publish_time, create_time) < {1})", to, from)
            .list();
        if (CollUtil.isEmpty(expiringList)) {
            return;
        }
        AppMessageService appMessageService = SpringUtil.getBean(AppMessageService.class);
        int warned = 0;
        for (FindCaseDO caseDO : expiringList) {
            if (!markWarned(caseDO.getId())) {
                // 本轮周期内已提醒过，避免每天重复打扰
                continue;
            }
            appMessageService.send(caseDO.getOwnerId(), NotificationEventEnum.CASE_EXPIRING, "FIND_CASE", caseDO
                .getId(), "档案即将退出广场", "您的寻人档案「" + caseDO.getName() + "（" + caseDO
                    .getCaseNo() + "）」将于 " + WARN_DAYS_BEFORE + " 天后自动归档并退出广场展示，请确认信息是否仍然有效并续期。");
            warned++;
        }
        log.info("本次临期提醒档案 {} 条", warned);
    }

    /**
     * 打上提醒标记
     *
     * @param caseId 档案 ID
     * @return true：本次首次提醒
     */
    private static boolean markWarned(Long caseId) {
        String key = EXPIRING_WARN_KEY_PREFIX + caseId;
        if (RedisUtils.get(key) != null) {
            return false;
        }
        RedisUtils.set(key, String.valueOf(caseId));
        RedisUtils.expire(key, Duration.ofDays(EXPIRE_DAYS));
        return true;
    }
}
