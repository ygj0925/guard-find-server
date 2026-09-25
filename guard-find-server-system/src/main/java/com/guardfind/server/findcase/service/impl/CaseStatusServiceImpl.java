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

package com.guardfind.server.findcase.service.impl;

import cn.hutool.core.util.StrUtil;
import com.guardfind.server.findcase.enums.CaseOperatorTypeEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.mapper.CaseStatusLogMapper;
import com.guardfind.server.findcase.mapper.FavoriteMapper;
import com.guardfind.server.findcase.mapper.FindCaseMapper;
import com.guardfind.server.findcase.model.entity.CaseStatusLogDO;
import com.guardfind.server.findcase.model.entity.FavoriteDO;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.service.CaseStatusService;
import com.guardfind.server.findcase.support.CaseConstants;
import com.guardfind.server.notification.enums.NotificationEventEnum;
import com.guardfind.server.notification.service.AppMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.util.validation.CheckUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 寻人档案状态流转业务实现
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseStatusServiceImpl implements CaseStatusService {

    private final FindCaseMapper findCaseMapper;
    private final CaseStatusLogMapper caseStatusLogMapper;
    private final FavoriteMapper favoriteMapper;
    private final AppMessageService appMessageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseStatusLogDO change(Long caseId,
                                  CaseStatusEnum target,
                                  String reasonCode,
                                  String reason,
                                  Long operatorId,
                                  CaseOperatorTypeEnum operatorType) {
        CheckUtils.throwIfNull(caseId, "档案 ID 不能为空");
        FindCaseDO caseDO = findCaseMapper.selectById(caseId);
        CheckUtils.throwIfNull(caseDO, "档案不存在或已删除");
        return this.change(caseDO, target, reasonCode, reason, operatorId, operatorType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseStatusLogDO change(FindCaseDO caseDO,
                                  CaseStatusEnum target,
                                  String reasonCode,
                                  String reason,
                                  Long operatorId,
                                  CaseOperatorTypeEnum operatorType) {
        CheckUtils.throwIfNull(caseDO, "档案不存在或已删除");
        CheckUtils.throwIfNull(target, "目标状态不能为空");
        CaseStatusEnum from = caseDO.getStatus();
        CheckUtils.throwIf(from == target, "档案已处于 [{}] 状态", target.getDescription());
        CheckUtils.throwIf(!CaseStatusEnum.canTransfer(from, target), "状态冲突：档案当前为 [{}]，不能变更为 [{}]", from == null
            ? "未知"
            : from.getDescription(), target.getDescription());
        CaseOperatorTypeEnum type = operatorType == null ? CaseOperatorTypeEnum.APP : operatorType;
        LocalDateTime now = LocalDateTime.now();
        // 1. 更新状态与随状态联动的时效字段
        findCaseMapper.updateById(this.buildStatusUpdate(caseDO, target, operatorId, now));
        // 2. 写状态轨迹（审计与"信息被谁改过"的依据）
        CaseStatusLogDO statusLog = this.buildStatusLog(caseDO
            .getId(), from, target, reasonCode, reason, operatorId, type, now);
        caseStatusLogMapper.insert(statusLog);
        // 3. 发通知（与状态变更同事务，推送失败由通知侧兜底）
        this.notifyChange(caseDO, target, reasonCode, reason, now);
        // 内存实体同步，便于调用方复用
        caseDO.setStatus(target);
        return statusLog;
    }

    /**
     * 构造状态更新实体（仅设置需要变更的列，避免整行覆盖）
     */
    private FindCaseDO buildStatusUpdate(FindCaseDO caseDO, CaseStatusEnum target, Long operatorId, LocalDateTime now) {
        FindCaseDO update = new FindCaseDO();
        update.setId(caseDO.getId());
        update.setStatus(target);
        update.setUpdateUser(operatorId);
        update.setUpdateTime(now);
        switch (target) {
            case PUBLISHED -> {
                if (caseDO.getPublishTime() == null) {
                    update.setPublishTime(now);
                }
                update.setLastConfirmTime(now);
            }
            case FOUND -> {
                update.setFoundTime(Objects.requireNonNullElse(caseDO.getFoundTime(), now));
                update.setLastConfirmTime(now);
            }
            case ARCHIVED -> update.setArchivedTime(now);
            default -> {
            }
        }
        return update;
    }

    /**
     * 构造状态轨迹
     */
    private CaseStatusLogDO buildStatusLog(Long caseId,
                                           CaseStatusEnum from,
                                           CaseStatusEnum target,
                                           String reasonCode,
                                           String reason,
                                           Long operatorId,
                                           CaseOperatorTypeEnum operatorType,
                                           LocalDateTime now) {
        CaseStatusLogDO statusLog = new CaseStatusLogDO();
        statusLog.setCaseId(caseId);
        statusLog.setFromStatus(from);
        statusLog.setToStatus(target);
        statusLog.setReasonCode(reasonCode);
        statusLog.setReason(reason);
        statusLog.setOperatorId(operatorId);
        statusLog.setOperatorType(operatorType.name());
        statusLog.setCreateUser(operatorId);
        statusLog.setCreateTime(now);
        statusLog.setUpdateUser(operatorId);
        statusLog.setUpdateTime(now);
        return statusLog;
    }

    /**
     * 按目标状态派发通知
     */
    private void notifyChange(FindCaseDO caseDO,
                              CaseStatusEnum target,
                              String reasonCode,
                              String reason,
                              LocalDateTime now) {
        Long ownerId = caseDO.getOwnerId();
        String caseName = StrUtil.blankToDefault(caseDO.getName(), "寻人档案");
        switch (target) {
            case PENDING_REVIEW -> this.send(ownerId, NotificationEventEnum.SYSTEM, caseDO
                .getId(), "已提交审核", "您发布的寻人档案「%s」已提交审核，我们将在 24 小时内完成核实。".formatted(caseName));
            case PUBLISHED -> {
                if ("RENEW".equals(reasonCode)) {
                    this.send(ownerId, NotificationEventEnum.CASE_EXPIRING, caseDO
                        .getId(), "档案已续期", "寻人档案「%s」已续期，重新在广场展示 %d 天。".formatted(caseName, CaseConstants.EXPIRY_DAYS));
                } else {
                    this.send(ownerId, NotificationEventEnum.CASE_APPROVED, caseDO
                        .getId(), "档案已发布", "您发布的寻人档案「%s」已通过审核，现已在广场对外展示。".formatted(caseName));
                }
            }
            case REJECTED -> this.send(ownerId, NotificationEventEnum.CASE_REJECTED, caseDO
                .getId(), "档案未通过审核", "寻人档案「%s」未通过审核：%s。补充完善后可重新提交。".formatted(caseName, StrUtil
                    .blankToDefault(reason, "请核对信息真实性")));
            case FOUND -> {
                this.send(ownerId, NotificationEventEnum.CASE_FOUND, caseDO.getId(), "找回喜报", "「%s」已确认找回，感谢所有提供线索的好心人。"
                    .formatted(caseName));
                this.notifyFavorites(caseDO, caseName);
            }
            case ARCHIVED -> this.send(ownerId, NotificationEventEnum.CASE_EXPIRING, caseDO
                .getId(), "档案已归档", "寻人档案「%s」已超过 %d 天无人确认，暂退出广场展示。在「我的发布」中续期即可重新发布。"
                    .formatted(caseName, CaseConstants.EXPIRY_DAYS));
            case WITHDRAWN -> this.send(ownerId, NotificationEventEnum.SYSTEM, caseDO
                .getId(), "档案已撤回", "寻人档案「%s」已撤回，不再对外展示。".formatted(caseName));
            case OFFLINE, BLOCKED -> this.send(ownerId, NotificationEventEnum.SYSTEM, caseDO
                .getId(), "档案已下架", "寻人档案「%s」因「%s」被下架，如有疑问请联系客服。".formatted(caseName, StrUtil
                    .blankToDefault(reason, "违反平台规范")));
            case ERASED -> this.send(ownerId, NotificationEventEnum.SYSTEM, caseDO
                .getId(), "档案已删除", "您申请的档案删除已完成，姓名、照片、地址等识别信息已清除。");
            default -> log.debug("状态 [{}] 无需通知，档案 ID：{}", target, caseDO.getId());
        }
    }

    /**
     * 关注该档案的用户同步收到喜报（一期以收藏关系近似"关注"）
     */
    private void notifyFavorites(FindCaseDO caseDO, String caseName) {
        if (caseDO.getId() == null) {
            return;
        }
        List<FavoriteDO> favorites = favoriteMapper.lambdaQuery()
            .eq(FavoriteDO::getTargetType, CaseConstants.TARGET_TYPE_CASE)
            .eq(FavoriteDO::getTargetId, caseDO.getId())
            .last("LIMIT " + CaseConstants.FOUND_NOTICE_FAVORITE_LIMIT)
            .list();
        for (FavoriteDO favorite : favorites) {
            if (Objects.equals(favorite.getUserId(), caseDO.getOwnerId())) {
                continue;
            }
            this.send(favorite.getUserId(), NotificationEventEnum.CASE_FOUND, caseDO
                .getId(), "关注的档案已找回", "您关注的寻人档案「%s」已确认找回。".formatted(caseName));
        }
    }

    private void send(Long receiverId, NotificationEventEnum event, Long caseId, String title, String content) {
        if (receiverId == null) {
            // 采集来源档案无发布人，跳过站内信（状态轨迹仍完整留存）
            return;
        }
        appMessageService.send(receiverId, event, CaseConstants.MESSAGE_BIZ_TYPE, caseId, title, content);
    }
}
