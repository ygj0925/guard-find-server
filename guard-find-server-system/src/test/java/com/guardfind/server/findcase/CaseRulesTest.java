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

package com.guardfind.server.findcase;

import com.guardfind.server.common.enums.GenderEnum;
import com.guardfind.server.findcase.enums.CaseStatusEnum;
import com.guardfind.server.findcase.model.entity.FindCaseDO;
import com.guardfind.server.findcase.model.entity.FindCaseMediaDO;
import com.guardfind.server.findcase.support.CaseSubmitValidator;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 档案状态机与提交校验单元测试
 */
class CaseRulesTest extends ContainerlessTestBase {

    private FindCaseDO completeCase() {
        FindCaseDO caseDO = new FindCaseDO();
        caseDO.setName("张三");
        caseDO.setGender(GenderEnum.MALE);
        caseDO.setCaseType("LOST");
        caseDO.setMissingTime(LocalDateTime.now().minusDays(3));
        caseDO.setProvinceCode("110000");
        caseDO.setCityCode("110100");
        caseDO.setDistrictCode("110105");
        caseDO.setHeight(120);
        caseDO.setAgreementAccepted(Boolean.TRUE);
        caseDO.setAgreementVersion("v1");
        return caseDO;
    }

    @Test
    @DisplayName("非法跃迁被拒，合法跃迁放行")
    void illegalTransitionsRejected() {
        assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.DRAFT, CaseStatusEnum.PUBLISHED)).isFalse();
        assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.REJECTED, CaseStatusEnum.PUBLISHED)).isFalse();
        assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.PUBLISHED, CaseStatusEnum.PUBLISHED)).isFalse();
        assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.ERASED, CaseStatusEnum.PUBLISHED)).isFalse();
        assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.PENDING_REVIEW, CaseStatusEnum.PUBLISHED)).isTrue();
        assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.PUBLISHED, CaseStatusEnum.FOUND)).isTrue();
        assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.ARCHIVED, CaseStatusEnum.PUBLISHED)).isTrue();
    }

    @Test
    @DisplayName("删除权优先：任意非 ERASED 状态都可转 ERASED，ERASED 是终态")
    void erasedIsTerminal() {
        for (CaseStatusEnum from : CaseStatusEnum.values()) {
            assertThat(CaseStatusEnum.canTransfer(from, CaseStatusEnum.ERASED))
                .isEqualTo(from != CaseStatusEnum.ERASED);
            if (from != CaseStatusEnum.ERASED) {
                assertThat(CaseStatusEnum.canTransfer(CaseStatusEnum.ERASED, from)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("对外可见集合只有 PUBLISHED，ERASED/OFFLINE 不可见")
    void onlyPublishedIsPublic() {
        assertThat(CaseStatusEnum.visiblePublicly()).containsExactly(CaseStatusEnum.PUBLISHED);
        assertThat(CaseStatusEnum.foundVisible()).containsExactly(CaseStatusEnum.FOUND);
    }

    @Test
    @DisplayName("完整档案可提交")
    void completeCasePasses() {
        assertThatCode(() -> CaseSubmitValidator.validate(completeCase(), List.of(), 0)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("缺体貌字段提交失败")
    void missingPhysicalFeatureFails() {
        FindCaseDO caseDO = completeCase();
        caseDO.setHeight(null);
        assertThatThrownBy(() -> CaseSubmitValidator.validate(caseDO, List.of(), 0)).hasMessageContaining("体貌");
        caseDO.setFeatures("左眉骨有旧疤");
        assertThatCode(() -> CaseSubmitValidator.validate(caseDO, List.of(), 0)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("失踪时间晚于当前、区划未到区县、未勾选告知都被拒")
    void spaceTimeAndAgreementGuarded() {
        FindCaseDO future = completeCase();
        future.setMissingTime(LocalDateTime.now().plusDays(1));
        assertThatThrownBy(() -> CaseSubmitValidator.validate(future, List.of(), 0)).hasMessageContaining("晚于当前");

        FindCaseDO noDistrict = completeCase();
        noDistrict.setDistrictCode(null);
        assertThatThrownBy(() -> CaseSubmitValidator.validate(noDistrict, List.of(), 0)).hasMessageContaining("区县");

        FindCaseDO noAgreement = completeCase();
        noAgreement.setAgreementAccepted(Boolean.FALSE);
        assertThatThrownBy(() -> CaseSubmitValidator.validate(noAgreement, List.of(), 0)).hasMessageContaining("告知");
    }

    @Test
    @DisplayName("每日 5 条上限与图片数量/类型白名单")
    void dailyLimitAndMediaWhitelist() {
        assertThatCode(() -> CaseSubmitValidator.validate(completeCase(), List.of(), 4)).doesNotThrowAnyException();
        assertThatThrownBy(() -> CaseSubmitValidator.validate(completeCase(), List.of(), 5)).hasMessageContaining("上限");

        List<FindCaseMediaDO> tooMany = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tooMany.add(media("PHOTO"));
        }
        assertThatThrownBy(() -> CaseSubmitValidator.validate(completeCase(), tooMany, 0)).hasMessageContaining("9");
        assertThatThrownBy(() -> CaseSubmitValidator.validate(completeCase(), List.of(media("GIF")), 0))
            .hasMessageContaining("PHOTO/VIDEO");
    }

    private FindCaseMediaDO media(String type) {
        FindCaseMediaDO media = new FindCaseMediaDO();
        media.setType(type);
        media.setUrl("https://cdn.example.org/a.jpg");
        return media;
    }
}
