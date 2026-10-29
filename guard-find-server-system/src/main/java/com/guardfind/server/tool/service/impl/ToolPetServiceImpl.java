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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guardfind.server.app.context.AppUserContextHolder;
import com.guardfind.server.app.exception.AppBizException;
import com.guardfind.server.tool.mapper.ToolPetLogMapper;
import com.guardfind.server.tool.mapper.ToolPetMapper;
import com.guardfind.server.tool.model.entity.ToolPetDO;
import com.guardfind.server.tool.model.entity.ToolPetLogDO;
import com.guardfind.server.tool.model.req.ToolPetLogReq;
import com.guardfind.server.tool.model.req.ToolPetReq;
import com.guardfind.server.tool.model.resp.ToolPetLogResp;
import com.guardfind.server.tool.model.resp.ToolPetResp;
import com.guardfind.server.tool.service.ToolPetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.starter.core.exception.BusinessException;

import java.util.List;

/**
 * 宠物成长记录实现。权限:仅主人可操作自己的宠物,越权/不存在同话术 NOT_FOUND。
 */
@Service
@RequiredArgsConstructor
public class ToolPetServiceImpl implements ToolPetService {

    private final ToolPetMapper petMapper;
    private final ToolPetLogMapper logMapper;

    @Override
    public Long create(ToolPetReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolPetDO pet = new ToolPetDO();
        applyReq(pet, req);
        pet.setUserId(userId);
        petMapper.insert(pet);
        return pet.getId();
    }

    @Override
    public void update(Long id, ToolPetReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolPetDO pet = getOwned(id, userId);
        applyReq(pet, req);
        petMapper.updateById(pet);
    }

    @Override
    public void delete(Long id) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        ToolPetDO pet = getOwned(id, userId);
        logMapper.delete(Wrappers.<ToolPetLogDO>lambdaQuery().eq(ToolPetLogDO::getPetId, pet.getId()));
        petMapper.deleteById(pet.getId());
    }

    @Override
    public List<ToolPetResp> list() {
        Long userId = AppUserContextHolder.getUserIdRequired();
        return petMapper.selectList(Wrappers.<ToolPetDO>lambdaQuery()
            .eq(ToolPetDO::getUserId, userId)
            .orderByDesc(ToolPetDO::getId)).stream().map(ToolPetServiceImpl::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addLog(Long petId, ToolPetLogReq req) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(petId, userId);
        ToolPetLogDO log = new ToolPetLogDO();
        log.setPetId(petId);
        log.setUserId(userId);
        log.setLogType(req.getLogType());
        log.setContent(StrUtil.trim(req.getContent()));
        log.setValue(req.getValue());
        log.setUnit(req.getUnit());
        log.setLogDate(req.getLogDate());
        logMapper.insert(log);
        return log.getId();
    }

    @Override
    public void deleteLog(Long petId, Long logId) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(petId, userId);
        logMapper.delete(Wrappers.<ToolPetLogDO>lambdaQuery()
            .eq(ToolPetLogDO::getId, logId)
            .eq(ToolPetLogDO::getPetId, petId));
    }

    @Override
    public List<ToolPetLogResp> logs(Long petId, String logType) {
        Long userId = AppUserContextHolder.getUserIdRequired();
        getOwned(petId, userId);
        return logMapper.selectList(Wrappers.<ToolPetLogDO>lambdaQuery()
            .eq(ToolPetLogDO::getPetId, petId)
            .eq(StrUtil.isNotBlank(logType), ToolPetLogDO::getLogType, logType)
            .orderByDesc(ToolPetLogDO::getLogDate)
            .orderByDesc(ToolPetLogDO::getId)).stream().map(ToolPetServiceImpl::toLogResp).toList();
    }

    private void applyReq(ToolPetDO pet, ToolPetReq req) {
        if (StrUtil.isBlank(req.getName()))
            throw new BusinessException("宠物名不能为空");
        pet.setName(StrUtil.trim(req.getName()));
        pet.setSpecies(req.getSpecies());
        pet.setBreed(StrUtil.trimToNull(req.getBreed()));
        pet.setGender(req.getGender());
        pet.setBirthday(req.getBirthday());
        pet.setWeightKg(req.getWeightKg());
        pet.setNotes(StrUtil.trimToNull(req.getNotes()));
    }

    private ToolPetDO getOwned(Long id, Long userId) {
        if (id == null)
            throw new BusinessException("宠物 ID 不能为空");
        ToolPetDO pet = petMapper.selectById(id);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new AppBizException(AppBizException.NOT_FOUND, "宠物不存在");
        }
        return pet;
    }

    private static ToolPetResp toResp(ToolPetDO pet) {
        ToolPetResp resp = new ToolPetResp();
        resp.setId(pet.getId());
        resp.setName(pet.getName());
        resp.setSpecies(pet.getSpecies());
        resp.setBreed(pet.getBreed());
        resp.setGender(pet.getGender());
        resp.setBirthday(pet.getBirthday());
        resp.setAvatar(pet.getAvatar());
        resp.setWeightKg(pet.getWeightKg());
        resp.setNotes(pet.getNotes());
        resp.setCreateTime(pet.getCreateTime());
        return resp;
    }

    private static ToolPetLogResp toLogResp(ToolPetLogDO log) {
        ToolPetLogResp resp = new ToolPetLogResp();
        resp.setId(log.getId());
        resp.setLogType(log.getLogType());
        resp.setContent(log.getContent());
        resp.setValue(log.getValue());
        resp.setUnit(log.getUnit());
        resp.setLogDate(log.getLogDate());
        return resp;
    }
}
