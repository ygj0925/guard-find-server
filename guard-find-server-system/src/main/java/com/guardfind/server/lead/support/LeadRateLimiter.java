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

package com.guardfind.server.lead.support;

import com.guardfind.server.app.exception.AppBizException;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.lead.constant.LeadConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.starter.cache.redisson.util.RedisUtils;
import top.continew.starter.ratelimiter.exception.RateLimiterException;

import java.time.Duration;

/**
 * 线索提交反滥用限频器
 *
 * <p>
 * 与 {@code @RateLimiter} 注解互为两道闸门：注解以切面方式拦截明显刷屏，本器以业务维度（同一用户对同一档案、
 * 同一 IP）做 Redis 计数，窗口 1 小时、首次计数时设置 TTL，命中阈值直接拒绝，保证限频不依赖注解也能生效
 * （后台补录、内部调用同样受限）。
 * </p>
 *
 * <p>
 * 降级策略：Redis 不可用时记录告警并放行。寻亲线索是救命通道，基础组件故障不应导致通道整体不可用，
 * 此时仍有 {@code @RateLimiter} 注解与内容风控兜底。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
public class LeadRateLimiter {

    /**
     * 占用一次提交额度，超限即抛出限流异常
     *
     * <p>
     * 先计用户维度再计 IP 维度；IP 维度拒绝时回退用户维度计数，避免同一 IP 的刷屏把无辜用户的额度吃掉。
     * </p>
     *
     * @param userId 提交人（App 用户 ID）
     * @param caseId 档案 ID
     * @param ip     客户端 IP（可为空，空时跳过 IP 维度）
     */
    public void acquire(Long userId, Long caseId, String ip) {
        this.check(LeadConstants
            .userCaseLimitKey(userId, caseId), LeadConstants.MAX_PER_USER_CASE_PER_HOUR, "您对该档案的线索提交过于频繁（每小时最多 %d 条），请 %s 后再提交");
        if (StrUtil.isBlank(ip)) {
            log.warn("线索提交未取到客户端 IP，跳过 IP 维度限频，userId={}，caseId={}", userId, caseId);
            return;
        }
        try {
            this.check(LeadConstants
                .ipLimitKey(ip), LeadConstants.MAX_PER_IP_PER_HOUR, "当前网络提交线索过于频繁（每小时最多 %d 条），请 %s 后再提交");
        } catch (RateLimiterException e) {
            // IP 维度被拒时本次请求不成立，回退已占用的用户额度
            this.release(LeadConstants.userCaseLimitKey(userId, caseId));
            throw e;
        }
    }

    /**
     * 计数并判定是否超限
     *
     * @param key     计数键
     * @param limit   窗口内上限
     * @param message 提示模板（%d 上限、%s 等待时长）
     */
    private void check(String key, int limit, String message) {
        long count;
        try {
            count = this.increment(key);
        } catch (Exception e) {
            log.warn("线索限频计数失败（本次放行），key={}：{}", key, e.getMessage());
            return;
        }
        if (count <= limit) {
            return;
        }
        throw new AppBizException(AppBizException.RATE_LIMITED, message.formatted(limit, this.waitDescription(key)));
    }

    /**
     * 计数 +1，并在窗口首次创建时设置 TTL
     *
     * @param key 计数键
     * @return 当前计数
     */
    private long increment(String key) {
        long count = RedisUtils.incr(key);
        if (count == 1L) {
            RedisUtils.expire(key, Duration.ofHours(LeadConstants.LIMIT_WINDOW_HOURS));
        }
        return count;
    }

    /**
     * 回退一次计数（键已归零时删除，避免留下 0 值键延长窗口）
     *
     * @param key 计数键
     */
    private void release(String key) {
        try {
            if (RedisUtils.decr(key) <= 0) {
                RedisUtils.delete(key);
            }
        } catch (Exception e) {
            log.warn("线索限频计数回退失败，key={}：{}", key, e.getMessage());
        }
    }

    /**
     * 窗口剩余等待时长文案
     *
     * @param key 计数键
     * @return 形如 "12 秒"、"35 分钟"
     */
    private String waitDescription(String key) {
        long millis;
        try {
            millis = RedisUtils.getTimeToLive(key);
        } catch (Exception e) {
            return Duration.ofHours(LeadConstants.LIMIT_WINDOW_HOURS).toSeconds() + " 秒";
        }
        if (millis <= 0) {
            millis = Duration.ofHours(LeadConstants.LIMIT_WINDOW_HOURS).toMillis();
        }
        Duration remaining = Duration.ofMillis(millis);
        return remaining.toMinutes() >= 1
            ? remaining.toMinutes() + (remaining.getSeconds() % 60 == 0
                ? " 分钟"
                : " 分 " + remaining.getSeconds() % 60 + " 秒")
            : remaining.toSeconds() + " 秒";
    }
}
