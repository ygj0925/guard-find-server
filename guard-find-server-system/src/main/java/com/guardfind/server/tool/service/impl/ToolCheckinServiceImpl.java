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

package com.guardfind.server.tool.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.app.mapper.AppUserMapper;
import com.guardfind.server.app.model.entity.AppUserDO;
import com.guardfind.server.tool.mapper.ToolCheckinMapper;
import com.guardfind.server.tool.mapper.ToolCheckinRecordMapper;
import com.guardfind.server.tool.model.entity.ToolCheckinDO;
import com.guardfind.server.tool.model.entity.ToolCheckinRecordDO;
import com.guardfind.server.tool.model.req.ToolCheckinCreateReq;
import com.guardfind.server.tool.model.req.ToolCheckinReq;
import com.guardfind.server.tool.model.resp.ToolCheckinResp;
import com.guardfind.server.tool.service.ToolCheckinService;
import com.guardfind.server.tool.support.CheckinCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 签到实现。防代签四道闸:
 * ① 动态码(30 秒轮换,服务端验签,接受上一窗做时钟偏移宽容);
 * ② 时间窗(窗口外拒签);
 * ③ 一人一次(唯一约束);
 * ④ 地点名校验(发起人设置了地点时,签到人填写须一致)。
 * 位置为用户主动填写,不申请定位权限。
 *
 * @author guardfind
 * @since 2026/10/29
 */
@Service
@RequiredArgsConstructor
public class ToolCheckinServiceImpl implements ToolCheckinService {

    private final ToolCheckinMapper checkinMapper;
    private final ToolCheckinRecordMapper recordMapper;
    private final AppUserMapper appUserMapper;

    @Override
    public Long create(ToolCheckinCreateReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        if (StrUtil.isBlank(req.getTitle())) {
            throw new BusinessException("签到主题不能为空");
        }
        LocalDateTime start = req.getStartTime() == null ? LocalDateTime.now() : req.getStartTime();
        LocalDateTime end = req.getEndTime() == null ? start.plusHours(2) : req.getEndTime();
        if (!end.isAfter(start)) {
            throw new BusinessException("截止时间必须晚于开始时间");
        }
        ToolCheckinDO activity = new ToolCheckinDO();
        activity.setUserId(userId);
        activity.setTitle(StrUtil.trim(req.getTitle()));
        activity.setLocationName(StrUtil.trimToNull(req.getLocationName()));
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setSecret(RandomUtil.randomString(16));
        checkinMapper.insert(activity);
        return activity.getId();
    }

    @Override
    public List<ToolCheckinResp> myActivities() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<ToolCheckinDO> activities = checkinMapper.selectList(Wrappers.<ToolCheckinDO>lambdaQuery()
            .eq(ToolCheckinDO::getUserId, userId)
            .orderByDesc(ToolCheckinDO::getId));
        return activities.stream().map(activity -> toResp(activity, true)).toList();
    }

    @Override
    public ToolCheckinResp detail(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolCheckinDO activity = getActivity(id);
        boolean isOwner = Objects.equals(activity.getUserId(), userId);
        ToolCheckinResp resp = toResp(activity, isOwner);
        if (isOwner) {
            // 名单仅发起人可见
            resp.setRecords(records(id));
        } else {
            ToolCheckinRecordDO mine = recordMapper.selectOne(Wrappers.<ToolCheckinRecordDO>lambdaQuery()
                .eq(ToolCheckinRecordDO::getActivityId, id)
                .eq(ToolCheckinRecordDO::getUserId, userId)
                .last("LIMIT 1"));
            if (mine != null) {
                resp.setSignedByMe(true);
                resp.setMyLocationName(mine.getLocationName());
            }
        }
        return resp;
    }

    @Override
    public Long checkin(Long id, ToolCheckinReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolCheckinDO activity = getActivity(id);
        if (Objects.equals(activity.getUserId(), userId)) {
            throw new BusinessException("发起人无需签到");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException("签到还没开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException("签到已截止");
        }
        // 动态码校验(当前窗或上一窗,时钟偏移宽容)
        long epochSeconds = now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        if (!CheckinCodeGenerator.verify(activity.getSecret(), epochSeconds, req.getCode())) {
            throw new BusinessException("签到码不正确或已过期,请输入屏幕上最新的码");
        }
        // 地点名校验:发起人设置了地点时,签到人须填写一致(去空格精确匹配)
        if (StrUtil.isNotBlank(activity.getLocationName())) {
            String filled = StrUtil.trimToEmpty(req.getLocationName());
            if (!filled.equals(activity.getLocationName())) {
                throw new BusinessException("所在地点与活动地点不一致,请核对后重试");
            }
        }
        // 一人一次(唯一约束兜底,先查给出友好提示)
        Long signed = recordMapper.selectCount(Wrappers.<ToolCheckinRecordDO>lambdaQuery()
            .eq(ToolCheckinRecordDO::getActivityId, id)
            .eq(ToolCheckinRecordDO::getUserId, userId));
        if (signed > 0) {
            throw new BusinessException("你已经签到过了");
        }
        ToolCheckinRecordDO record = new ToolCheckinRecordDO();
        record.setActivityId(id);
        record.setUserId(userId);
        record.setLocationName(StrUtil.trimToNull(req.getLocationName()));
        recordMapper.insert(record);
        return record.getId();
    }

    @Override
    public List<ToolCheckinResp> signedByMe() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        List<ToolCheckinRecordDO> records = recordMapper.selectList(Wrappers.<ToolCheckinRecordDO>lambdaQuery()
            .eq(ToolCheckinRecordDO::getUserId, userId)
            .orderByDesc(ToolCheckinRecordDO::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, ToolCheckinDO> activities = checkinMapper.selectByIds(records.stream()
            .map(ToolCheckinRecordDO::getActivityId)
            .toList()).stream().collect(Collectors.toMap(ToolCheckinDO::getId, Function.identity()));
        return records.stream().filter(record -> activities.containsKey(record.getActivityId())).map(record -> {
            ToolCheckinResp resp = toResp(activities.get(record.getActivityId()), false);
            resp.setSignedByMe(true);
            resp.setMyLocationName(record.getLocationName());
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolCheckinDO activity = getActivity(id);
        if (!Objects.equals(activity.getUserId(), userId)) {
            throw new AppBizException(AppBizException.NOT_FOUND, "签到活动不存在");
        }
        recordMapper.delete(Wrappers.<ToolCheckinRecordDO>lambdaQuery().eq(ToolCheckinRecordDO::getActivityId, id));
        checkinMapper.deleteById(activity.getId());
    }

    /** 签到名单(发起人):批量补昵称头像 */
    private List<ToolCheckinResp.CheckinRecord> records(Long activityId) {
        List<ToolCheckinRecordDO> records = recordMapper.selectList(Wrappers.<ToolCheckinRecordDO>lambdaQuery()
            .eq(ToolCheckinRecordDO::getActivityId, activityId)
            .orderByAsc(ToolCheckinRecordDO::getId));
        List<Long> userIds = records.stream().map(ToolCheckinRecordDO::getUserId).distinct().toList();
        Map<Long, AppUserDO> users = userIds.isEmpty()
            ? Map.of()
            : appUserMapper.selectByIds(userIds)
                .stream()
                .collect(Collectors.toMap(AppUserDO::getId, Function.identity()));
        return records.stream().map(record -> {
            ToolCheckinResp.CheckinRecord item = new ToolCheckinResp.CheckinRecord();
            item.setUserId(record.getUserId());
            item.setLocationName(record.getLocationName());
            item.setCreateTime(record.getCreateTime());
            AppUserDO user = users.get(record.getUserId());
            if (user != null) {
                item.setNickname(user.getNickname());
                item.setAvatar(user.getAvatar());
            }
            return item;
        }).toList();
    }

    private ToolCheckinDO getActivity(Long id) {
        if (id == null) {
            throw new BusinessException("签到活动 ID 不能为空");
        }
        ToolCheckinDO activity = checkinMapper.selectById(id);
        if (activity == null) {
            throw new AppBizException(AppBizException.NOT_FOUND, "签到活动不存在");
        }
        return activity;
    }

    private ToolCheckinResp toResp(ToolCheckinDO activity, boolean isOwner) {
        LocalDateTime now = LocalDateTime.now();
        ToolCheckinResp resp = new ToolCheckinResp();
        resp.setId(activity.getId());
        resp.setTitle(activity.getTitle());
        resp.setLocationName(activity.getLocationName());
        resp.setStartTime(activity.getStartTime());
        resp.setEndTime(activity.getEndTime());
        resp.setInWindow(now.isAfter(activity.getStartTime()) && now.isBefore(activity.getEndTime()));
        resp.setSignedCount(recordMapper.selectCount(Wrappers.<ToolCheckinRecordDO>lambdaQuery()
            .eq(ToolCheckinRecordDO::getActivityId, activity.getId())));
        // 种子仅发起人可见;参与者只输码,拿不到种子就无法自行生成
        if (isOwner) {
            resp.setSecret(activity.getSecret());
        }
        return resp;
    }
}
