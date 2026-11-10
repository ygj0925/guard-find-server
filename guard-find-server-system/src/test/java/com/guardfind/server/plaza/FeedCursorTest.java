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

import cn.hutool.core.codec.Base64;
import com.guardfind.server.plaza.enums.FeedSortEnum;
import com.guardfind.server.plaza.util.FeedCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 广场游标与排序流单元测试
 */
class FeedCursorTest {

    @Test
    @DisplayName("游标编解码往返一致，空游标表示首屏")
    void roundTrip() {
        String cursor = FeedCursor.encode(1_700_000_000_123L, 42L);
        assertThat(FeedCursor.decode(cursor)).isEqualTo(FeedCursor.of(1_700_000_000_123L, 42L));
        assertThat(FeedCursor.decode(null)).isNull();
        assertThat(FeedCursor.decode("  ")).isNull();
    }

    @Test
    @DisplayName("篡改或旧版本游标被拒，不静默回首屏")
    void tamperedCursorRejected() {
        String oldVersion = Base64.encodeUrlSafe("v0|1|2".getBytes(StandardCharsets.UTF_8));
        String notNumber = Base64.encodeUrlSafe("v1|abc|2".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> FeedCursor.decode(oldVersion)).hasMessageContaining("游标无效");
        assertThatThrownBy(() -> FeedCursor.decode(notNumber)).hasMessageContaining("游标无效");
        assertThatThrownBy(() -> FeedCursor.decode("not-a-cursor")).hasMessageContaining("游标无效");
    }

    @Test
    @DisplayName("排序键与时间列互转保留毫秒精度")
    void sortKeyPreservesMillis() {
        LocalDateTime time = LocalDateTime.of(2026, 9, 24, 10, 30, 15, 123_000_000);
        assertThat(FeedCursor.toLocalDateTime(FeedCursor.toSortKey(time))).isEqualTo(time);
        assertThat(FeedCursor.toSortKey(null)).isZero();
    }

    @Test
    @DisplayName("只有 found 流包含已找回，latest/urgent/nearby 不含")
    void onlyFoundStreamIncludesFound() {
        assertThat(FeedSortEnum.LATEST.includeFound()).isFalse();
        assertThat(FeedSortEnum.URGENT.includeFound()).isFalse();
        assertThat(FeedSortEnum.NEARBY.includeFound()).isFalse();
        assertThat(FeedSortEnum.FOUND.includeFound()).isTrue();
        assertThat(FeedSortEnum.of(" Latest ")).isEqualTo(FeedSortEnum.LATEST);
        assertThat(FeedSortEnum.of("hot")).isNull();
    }
}
