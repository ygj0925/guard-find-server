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

package com.guardfind.server.plaza.model.resp;

import cn.hutool.core.collection.CollUtil;
import com.guardfind.server.plaza.util.FeedCursor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 信息流游标分页响应
 *
 * @param <T> 记录类型
 * @author guardfind
 * @since 2026/9/24
 */
@Data
@Schema(description = "信息流游标分页响应")
public class FeedCursorResp<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录列表
     */
    @Schema(description = "记录列表")
    private List<T> records = new ArrayList<>();

    /**
     * 下一页游标（无更多数据时为 null）
     */
    @Schema(description = "下一页游标，无更多数据时为 null", example = "djF8MTcyMTgwMDAwMDAwMHwxNzkwMDAwMDAwMDAwMDAwMA")
    private String nextCursor;

    /**
     * 是否还有更多数据
     */
    @Schema(description = "是否还有更多数据", example = "true")
    private boolean hasMore;

    /**
     * 构造空响应
     *
     * @return 空响应
     */
    public static <T> FeedCursorResp<T> empty() {
        FeedCursorResp<T> resp = new FeedCursorResp<>();
        resp.setRecords(new ArrayList<>());
        resp.setHasMore(false);
        resp.setNextCursor(null);
        return resp;
    }

    /**
     * 由"多取一条"的原始结果构造分页响应
     *
     * <p>
     * 查询固定取 {@code size + 1} 条：多出的一条只用于判断 {@code hasMore}，不返回给客户端；
     * {@code nextCursor} 取本页最后一条的排序键与主键，客户端下次带上即可继续。
     * </p>
     *
     * @param fetched   原始结果（最多 size + 1 条，按排序键倒序）
     * @param size      每页条数
     * @param sortKeyFn 排序键取值函数
     * @param idFn      主键取值函数
     * @param <T>       记录类型
     * @return 分页响应
     */
    public static <T> FeedCursorResp<T> of(List<T> fetched,
                                           int size,
                                           Function<T, Long> sortKeyFn,
                                           Function<T, Long> idFn) {
        return FeedCursorResp.of(fetched, size, sortKeyFn, idFn, list -> new ArrayList<>(list));
    }

    /**
     * 由"多取一条"的原始结果构造分页响应，并把实体页转换为视图页
     *
     * @param fetched   原始结果（最多 size + 1 条，按排序键倒序）
     * @param size      每页条数
     * @param sortKeyFn 原始行排序键取值函数
     * @param idFn      原始行主键取值函数
     * @param converter 原始行 → 视图转换函数（入参为截断到 size 的页数据）
     * @param <S>       原始行类型
     * @param <T>       记录类型
     * @return 分页响应
     */
    public static <S, T> FeedCursorResp<T> of(List<S> fetched,
                                              int size,
                                              Function<S, Long> sortKeyFn,
                                              Function<S, Long> idFn,
                                              Function<List<S>, List<T>> converter) {
        if (CollUtil.isEmpty(fetched)) {
            return FeedCursorResp.empty();
        }
        boolean hasMore = fetched.size() > size;
        List<S> page = new ArrayList<>(fetched.subList(0, Math.min(size, fetched.size())));
        FeedCursorResp<T> resp = new FeedCursorResp<>();
        resp.setRecords(converter.apply(page));
        resp.setHasMore(hasMore);
        if (hasMore && !page.isEmpty()) {
            S last = page.get(page.size() - 1);
            resp.setNextCursor(FeedCursor.encode(sortKeyFn.apply(last), idFn.apply(last)));
        }
        return resp;
    }
}
