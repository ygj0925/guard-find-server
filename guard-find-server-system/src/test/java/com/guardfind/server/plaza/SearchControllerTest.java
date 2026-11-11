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

package com.guardfind.server.plaza;

import com.guardfind.server.common.config.exception.GlobalExceptionHandler;
import com.guardfind.server.findcase.model.query.CaseFeedQuery;
import com.guardfind.server.plaza.controller.SearchController;
import com.guardfind.server.plaza.model.resp.FeedCursorResp;
import com.guardfind.server.plaza.service.PlazaService;
import com.guardfind.server.support.ContainerlessTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchControllerTest extends ContainerlessTestBase {

    private final PlazaService service = mock(PlazaService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SearchController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @ParameterizedTest
    @CsvSource({"yearFrom, 1899", "yearFrom, 2201", "yearTo, 1899", "yearTo, 2201", "size, 0", "size, 51"})
    void invalidQueryReturnsParameter400BeforeCallingService(String field, int value) throws Exception {
        var result = mvc.perform(get("/app/search").param(field, Integer.toString(value)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.success").value(false))
            .andReturn();

        assertThat(result.getResolvedException()).isInstanceOfSatisfying(BindException.class, exception -> {
            assertThat(exception.getBindingResult().getFieldErrors()).extracting(FieldError::getField)
                .containsExactly(field);
            assertThat(exception.getBindingResult().getFieldError(field).getRejectedValue()).isEqualTo(value);
        });
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"1900, 1900, 1", "2200, 2200, 50", "2020, 2026, 10"})
    void validQueryCallsServiceWithBoundValues(int yearFrom, int yearTo, int size) throws Exception {
        when(service.search(any(CaseFeedQuery.class))).thenReturn(FeedCursorResp.empty());

        mvc.perform(get("/app/search").param("keyword", "张三")
            .param("yearFrom", Integer.toString(yearFrom))
            .param("yearTo", Integer.toString(yearTo))
            .param("size", Integer.toString(size))).andExpect(status().isOk());

        var query = ArgumentCaptor.forClass(CaseFeedQuery.class);
        verify(service).search(query.capture());
        assertThat(query.getValue().getKeyword()).isEqualTo("张三");
        assertThat(query.getValue().getYearFrom()).isEqualTo(yearFrom);
        assertThat(query.getValue().getYearTo()).isEqualTo(yearTo);
        assertThat(query.getValue().getSize()).isEqualTo(size);
        verifyNoMoreInteractions(service);
    }
}
