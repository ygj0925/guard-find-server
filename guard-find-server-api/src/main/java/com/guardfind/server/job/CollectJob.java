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
import com.guardfind.server.collect.constant.CollectConstants;
import com.guardfind.server.collect.model.resp.CollectRunResp;
import com.guardfind.server.collect.model.resp.CollectSourceResp;
import com.guardfind.server.collect.service.CollectOrchestrator;
import com.guardfind.server.collect.service.CollectSourceService;
import com.guardfind.server.schedule.annotation.ConditionalOnEnabledScheduleJob;
import top.continew.starter.core.constant.PropertiesConstants;
import top.continew.starter.extension.tenant.annotation.TenantIgnore;

import java.util.List;

/**
 * 寻亲数据采集任务
 *
 * <p>
 * 逐源判断 {@code enabled}（默认关闭）：只有运营在取得站方书面授权后显式开启的源才会被遍历，未开启的源一次请求都不会发出。
 * 单个源失败（含 robots 中止）只记录并继续下一个源，不影响其余源；本轮的页数、新增、失败与中止原因由编排器落
 * {@code gf_collect_log}。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectJob {

    /**
     * 定时采集（未启用 Snail Job 则使用它）
     */
    @Component
    @ConditionalOnProperty(prefix = "snail-job", name = PropertiesConstants.ENABLED, havingValue = "false")
    public static class Scheduler {

        @TenantIgnore
        @Scheduled(cron = "0 30 3 * * ?")
        public void collectWithSchedule() {
            log.info("定时任务 [寻亲数据采集] 开始执行。");
            execute();
            log.info("定时任务 [寻亲数据采集] 执行结束。");
        }
    }

    /**
     * 定时采集（启用 Snail Job 时）
     */
    @Component
    @ConditionalOnEnabledScheduleJob
    public static class ScheduleJob {

        @TenantIgnore
        @JobExecutor(name = "CollectJob")
        public void collectWithScheduleJob() {
            SnailJobLog.REMOTE.info("定时任务 [寻亲数据采集] 开始执行。");
            execute();
            SnailJobLog.REMOTE.info("定时任务 [寻亲数据采集] 执行结束。");
        }
    }

    /**
     * 遍历已启用的采集源依次执行一轮采集
     */
    private static void execute() {
        CollectSourceService collectSourceService = SpringUtil.getBean(CollectSourceService.class);
        CollectOrchestrator collectOrchestrator = SpringUtil.getBean(CollectOrchestrator.class);
        List<CollectSourceResp> sources = collectSourceService.listEnabled();
        if (CollUtil.isEmpty(sources)) {
            log.info("无已启用采集源（enabled=false 的源一律跳过），本轮不发起任何请求");
            return;
        }
        for (CollectSourceResp source : sources) {
            try {
                CollectRunResp result = collectOrchestrator.run(source.getCode(), null, CollectConstants.TRIGGER_JOB);
                log.info("采集源 [{}] 本轮完成：页数 {}，抓取 {}，新增 {}，重复 {}，失败 {}，耗时 {}ms{}", source.getCode(), result
                    .getPages(), result.getFetched(), result.getAdded(), result.getDuplicated(), result
                        .getFailed(), result.getCostMs(), result.isSuccess()
                            ? ""
                            : "，中止原因：" + result.getSkippedReason());
            } catch (Exception e) {
                // 单源异常不外溢，避免一个站点抖动导致整轮任务其他源不采
                log.error("采集源 [{}] 本轮执行失败", source.getCode(), e);
            }
        }
    }
}
