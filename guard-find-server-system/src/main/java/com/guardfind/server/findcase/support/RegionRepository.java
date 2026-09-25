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
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guardfind.server.findcase.model.resp.RegionNodeResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.continew.starter.core.constant.StringConstants;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 行政区划数据仓库
 *
 * <p>
 * 数据来源为随服务发布的 {@code china.json}（省-市-区三级，含 fullname），首次访问时一次性载入并常驻内存，
 * 用于把区划码翻译成"河北省 邯郸市"这类展示文本，以及给广场筛选器提供区划树。资源缺失时降级为"只输出编码"，
 * 不阻断接口（宁可少展示，不可让信息流 500）。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
@Slf4j
@Component
public class RegionRepository {

    /**
     * 区划数据资源名（位于 guard-find-server-api 的 resources 下，运行时同属一个 classpath）
     */
    private static final String REGION_RESOURCE = "china.json";

    /**
     * 懒加载后的区划树（volatile 保证多线程可见，加载失败时为空列表且不再重试）
     */
    private volatile List<RegionNodeResp> regionTree;

    /**
     * 区划编码 → 展示名称
     */
    private volatile Map<String, String> nameMap;

    /**
     * 区划树（省 → 市 → 区县）
     *
     * @return 区划树，数据缺失时为空列表
     */
    public List<RegionNodeResp> tree() {
        ensureLoaded();
        return regionTree;
    }

    /**
     * 区划编码转展示名称
     *
     * @param code 区划编码
     * @return 名称，无法判定时返回 null
     */
    public String nameOf(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        ensureLoaded();
        return nameMap.get(code.trim());
    }

    /**
     * 拼接区划文本（只到市，公开视图用）
     *
     * @param provinceCode 省编码
     * @param cityCode     市编码
     * @return 形如"河北省 邯郸市"的文本
     */
    public String joinToCity(String provinceCode, String cityCode) {
        List<String> names = new ArrayList<>(2);
        addIfPresent(names, this.nameOf(provinceCode));
        addIfPresent(names, this.nameOf(cityCode));
        return String.join(StringConstants.SPACE, names);
    }

    /**
     * 拼接完整区划文本（用于户籍地等完整视图）
     *
     * @param provinceCode 省编码
     * @param cityCode     市编码
     * @param districtCode 区县编码
     * @return 形如"河北省邯郸市曲周县"的文本
     */
    public String joinFull(String provinceCode, String cityCode, String districtCode) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, this.nameOf(provinceCode));
        appendIfPresent(builder, this.nameOf(cityCode));
        appendIfPresent(builder, this.nameOf(districtCode));
        return builder.isEmpty() ? null : builder.toString();
    }

    /**
     * 区划编码集合（供筛选参数合法性校验，避免为不存在的地市码开洞）
     *
     * @param code 区划编码
     * @return true：数据中存在该编码
     */
    public boolean contains(String code) {
        if (StrUtil.isBlank(code)) {
            return false;
        }
        ensureLoaded();
        return nameMap.containsKey(code.trim());
    }

    private void appendIfPresent(StringBuilder builder, String name) {
        if (StrUtil.isNotBlank(name)) {
            builder.append(name);
        }
    }

    private void addIfPresent(List<String> names, String name) {
        if (StrUtil.isNotBlank(name)) {
            names.add(name);
        }
    }

    private void ensureLoaded() {
        if (regionTree != null) {
            return;
        }
        synchronized (this) {
            if (regionTree != null) {
                return;
            }
            load();
        }
    }

    private void load() {
        List<RegionNodeResp> tree = new ArrayList<>();
        Map<String, String> nameIndex = new HashMap<>(4096);
        try {
            ClassPathResource resource = new ClassPathResource(REGION_RESOURCE);
            String content;
            content = IoUtil.read(resource.getStream(), StandardCharsets.UTF_8);
            JSONObject root = JSONUtil.parseObj(content);
            JSONArray provinces = root.getJSONArray("children");
            if (CollUtil.isNotEmpty(provinces)) {
                for (Object province : provinces) {
                    tree.add(toNode((JSONObject)province, nameIndex));
                }
            }
        } catch (Exception e) {
            log.warn("行政区划数据 [{}] 载入失败，区划文本降级为编码输出：{}", REGION_RESOURCE, e.getMessage());
        }
        this.regionTree = tree;
        this.nameMap = nameIndex;
    }

    private RegionNodeResp toNode(JSONObject json, Map<String, String> nameIndex) {
        RegionNodeResp node = new RegionNodeResp();
        String code = json.getStr("code");
        node.setCode(code);
        String name = StrUtil.blankToDefault(json.getStr("fullname"), json.getStr("name"));
        node.setName(name);
        node.setLevel(json.getStr("level"));
        if (StrUtil.isNotBlank(code) && StrUtil.isNotBlank(name)) {
            nameIndex.put(code, name);
        }
        List<RegionNodeResp> children = new ArrayList<>();
        JSONArray array = json.getJSONArray("children");
        if (CollUtil.isNotEmpty(array)) {
            for (Object child : array) {
                children.add(toNode((JSONObject)child, nameIndex));
            }
        }
        node.setChildren(children);
        return node;
    }
}
