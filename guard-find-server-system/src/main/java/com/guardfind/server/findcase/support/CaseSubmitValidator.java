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

package com.guardfind.server.findcase.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.FindCaseMediaDO;
import top.continew.starter.core.util.validation.ValidationUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交审核前的完整性与风控校验
 *
 * <p>
 * 校验规则集中于此，便于单测覆盖，也保证"直接提交"与"草稿后提交"两条路径口径一致。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class CaseSubmitValidator {

    /**
     * 单用户每日最多提交条数（防止批量灌水）
     */
    public static final int MAX_DAILY_SUBMIT = 5;

    /**
     * 单档案图片上限
     */
    public static final int MAX_MEDIA_COUNT = 9;

    /**
     * 视频上限（一期以图片为主，视频不超过 3 段）
     */
    public static final int MAX_VIDEO_COUNT = 3;

    private CaseSubmitValidator() {
    }

    /**
     * 校验档案是否具备提交审核的条件
     *
     * @param caseDO           档案实体
     * @param mediaList        档案媒体列表
     * @param todaySubmitCount 当日已提交条数（不含本次）
     */
    public static void validate(FindCaseDO caseDO, List<FindCaseMediaDO> mediaList, long todaySubmitCount) {
        ValidationUtils.throwIf(StrUtil.isBlank(caseDO.getName()), "提交失败：姓名不能为空");
        ValidationUtils.throwIf(caseDO.getGender() == null, "提交失败：性别不能为空");
        ValidationUtils.throwIf(StrUtil.isBlank(caseDO.getCaseType()), "提交失败：失踪情形不能为空");
        // 时空：失踪时间不得晚于当前，区划必须到区县
        ValidationUtils.throwIf(caseDO.getMissingTime() == null, "提交失败：失踪时间不能为空");
        ValidationUtils.throwIf(caseDO.getMissingTime().isAfter(LocalDateTime.now()), "提交失败：失踪时间不能晚于当前时间");
        ValidationUtils.throwIf(StrUtil.isBlank(caseDO.getProvinceCode()), "提交失败：失踪地省份不能为空");
        ValidationUtils.throwIf(StrUtil.isBlank(caseDO.getCityCode()), "提交失败：失踪地城市不能为空");
        ValidationUtils.throwIf(StrUtil.isBlank(caseDO.getDistrictCode()), "提交失败：失踪地区县不能为空");
        // 体貌：至少一项可识别特征，否则线索无法比对
        ValidationUtils.throwIf(!hasPhysicalFeature(caseDO), "提交失败：请至少填写一项体貌信息（身高、体型、疤痕/胎记等体貌特征）");
        // 媒体：数量与类型白名单
        validateMedia(mediaList);
        // 合规告知：必须显式勾选
        ValidationUtils.throwIf(!Boolean.TRUE.equals(caseDO.getAgreementAccepted()), "提交失败：请先阅读并确认《寻亲信息发布告知》");
        ValidationUtils.throwIf(StrUtil.isBlank(caseDO.getAgreementVersion()), "提交失败：合规告知版本号不能为空");
        // 频次：每日上限
        ValidationUtils.throwIf(todaySubmitCount >= MAX_DAILY_SUBMIT, "今日提交次数已达上限（每日 {} 条），请明天再继续", MAX_DAILY_SUBMIT);
    }

    /**
     * 是否具备至少一项体貌特征
     *
     * @param caseDO 档案实体
     * @return true：至少一项
     */
    public static boolean hasPhysicalFeature(FindCaseDO caseDO) {
        return caseDO.getHeight() != null || StrUtil.isNotBlank(caseDO.getBodyType()) || StrUtil.isNotBlank(caseDO
            .getFeatures());
    }

    private static void validateMedia(List<FindCaseMediaDO> mediaList) {
        if (CollUtil.isEmpty(mediaList)) {
            return;
        }
        ValidationUtils.throwIf(mediaList.size() > MAX_MEDIA_COUNT, "图片数量不能超过 {} 张", MAX_MEDIA_COUNT);
        long videoCount = mediaList.stream().filter(media -> StrUtil.equals(media.getType(), "VIDEO")).count();
        ValidationUtils.throwIf(videoCount > MAX_VIDEO_COUNT, "视频数量不能超过 {} 段", MAX_VIDEO_COUNT);
        mediaList.forEach(media -> {
            ValidationUtils.throwIf(StrUtil.isBlank(media.getUrl()), "影像地址不能为空");
            ValidationUtils.throwIf(!StrUtil.equalsAny(media.getType(), "PHOTO", "VIDEO"), "影像类型仅支持 PHOTO/VIDEO");
        });
    }
}
