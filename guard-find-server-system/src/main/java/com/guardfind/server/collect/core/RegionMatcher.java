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

package com.guardfind.server.collect.core;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 区划名称 → 行政区划码匹配器（采集归一化专用，best-effort）
 *
 * <p>
 * 采集到的失踪地点是"河北省邯郸市曲周县小河道村"这类自然文本，而档案与筛选依赖区划码。此处内置一份精简的
 * 省/市（含直辖市市辖区与少数市辖区）名录做前缀与包含匹配：命中几级就填几级，<b>未命中的编码留空但保留
 * {@code regionText} 原文</b>，由审核人补录，绝不做"猜一个码"的臆断。
 * </p>
 *
 * @author guardfind
 * @since 2026/9/24
 */
public final class RegionMatcher {

    /**
     * 内置区划名录（{@code 编码|名称}，按省分组，分号分隔）
     */
    private static final String REGION_DATA = """
        110000|北京市;110100|北京市;110101|东城区;110102|西城区;110105|朝阳区;110106|丰台区;110107|石景山区;110108|海淀区;110109|门头沟区;110111|房山区;110112|通州区;110113|顺义区;110114|昌平区;110115|大兴区
        120000|天津市;120100|天津市;120101|和平区;120102|河东区;120103|河西区;120104|南开区;120105|河北区;120106|红桥区;120110|东丽区;120111|西青区;120112|津南区;120113|北辰区;120114|武清区
        130000|河北省;130100|石家庄市;130200|唐山市;130300|秦皇岛市;130400|邯郸市;130500|邢台市;130600|保定市;130700|张家口市;130800|承德市;130900|沧州市;131000|廊坊市;131100|衡水市
        140000|山西省;140100|太原市;140200|大同市;140300|阳泉市;140400|长治市;140500|晋城市;140600|朔州市;140700|晋中市;140800|运城市;140900|忻州市;141000|临汾市;141100|吕梁市
        150000|内蒙古自治区;150100|呼和浩特市;150200|包头市;150300|乌海市;150400|赤峰市;150500|通辽市;150600|鄂尔多斯市;150700|呼伦贝尔市;150800|巴彦淖尔市;150900|乌兰察布市
        210000|辽宁省;210100|沈阳市;210200|大连市;210300|鞍山市;210400|抚顺市;210500|本溪市;210600|丹东市;210700|锦州市;210800|营口市;210900|阜新市;211000|辽阳市;211100|盘锦市;211200|铁岭市;211300|朝阳市;211400|葫芦岛市
        220000|吉林省;220100|长春市;220200|吉林市;220300|四平市;220400|辽源市;220500|通化市;220600|白山市;220700|松原市;220800|白城市;222400|延边州
        230000|黑龙江省;230100|哈尔滨市;230200|齐齐哈尔市;230300|鸡西市;230400|鹤岗市;230500|双鸭山市;230600|大庆市;230700|伊春市;230800|佳木斯市;230900|七台河市;231000|牡丹江市;231100|黑河市;231200|绥化市
        310000|上海市;310100|上海市;310101|黄浦区;310104|徐汇区;310105|长宁区;310106|静安区;310107|普陀区;310109|虹口区;310110|杨浦区;310112|闵行区;310113|宝山区;310114|嘉定区;310115|浦东新区
        320000|江苏省;320100|南京市;320200|无锡市;320300|徐州市;320400|常州市;320500|苏州市;320600|南通市;320700|连云港市;320800|淮安市;320900|盐城市;321000|扬州市;321100|镇江市;321200|泰州市;321300|宿迁市
        330000|浙江省;330100|杭州市;330200|宁波市;330300|温州市;330400|嘉兴市;330500|湖州市;330600|绍兴市;330700|金华市;330800|衢州市;330900|舟山市;331000|台州市;331100|丽水市
        340000|安徽省;340100|合肥市;340200|芜湖市;340300|蚌埠市;340400|淮南市;340500|马鞍山市;340600|淮北市;340700|铜陵市;340800|安庆市;341000|黄山市;341100|滁州市;341200|阜阳市;341300|宿州市;341500|六安市;341600|亳州市;341700|池州市;341800|宣城市
        350000|福建省;350100|福州市;350200|厦门市;350300|莆田市;350400|三明市;350500|泉州市;350600|漳州市;350700|南平市;350800|龙岩市;350900|宁德市
        360000|江西省;360100|南昌市;360200|景德镇市;360300|萍乡市;360400|九江市;360500|新余市;360600|鹰潭市;360700|赣州市;360800|吉安市;360900|宜春市;361000|抚州市;361100|上饶市
        370000|山东省;370100|济南市;370200|青岛市;370300|淄博市;370400|枣庄市;370500|东营市;370600|烟台市;370700|潍坊市;370800|济宁市;370900|泰安市;371000|威海市;371100|日照市;371300|临沂市;371400|德州市;371500|聊城市;371600|滨州市;371700|菏泽市
        410000|河南省;410100|郑州市;410200|开封市;410300|洛阳市;410400|平顶山市;410500|安阳市;410600|鹤壁市;410700|新乡市;410800|焦作市;410900|濮阳市;411000|许昌市;411100|漯河市;411200|三门峡市;411300|南阳市;411400|商丘市;411500|信阳市;411600|周口市;411700|驻马店市
        420000|湖北省;420100|武汉市;420200|黄石市;420300|十堰市;420500|宜昌市;420600|襄阳市;420700|鄂州市;420800|荆门市;420900|孝感市;421000|荆州市;421100|黄冈市;421200|咸宁市;421300|随州市;422800|恩施州
        430000|湖南省;430100|长沙市;430200|株洲市;430300|湘潭市;430400|衡阳市;430500|邵阳市;430600|岳阳市;430700|常德市;430800|张家界市;430900|益阳市;431000|郴州市;431100|永州市;431200|怀化市;431300|娄底市;433100|湘西州
        440000|广东省;440100|广州市;440200|韶关市;440300|深圳市;440400|珠海市;440500|汕头市;440600|佛山市;440700|江门市;440800|湛江市;440900|茂名市;441200|肇庆市;441300|惠州市;441400|梅州市;441500|汕尾市;441600|河源市;441700|阳江市;441800|清远市;441900|东莞市;442000|中山市;445100|潮州市;445200|揭阳市;445300|云浮市
        450000|广西壮族自治区;450100|南宁市;450200|柳州市;450300|桂林市;450400|梧州市;450500|北海市;450600|防城港市;450700|钦州市;450800|贵港市;450900|玉林市;451000|百色市;451100|贺州市;451200|河池市;451300|来宾市;451400|崇左市
        460000|海南省;460100|海口市;460200|三亚市;460300|三沙市;460400|儋州市
        500000|重庆市;500100|重庆市;500101|万州区;500103|渝中区;500106|沙坪坝区;500107|九龙坡区;500108|南岸区;500109|北碚区;500110|綦江区;500113|巴南区
        510000|四川省;510100|成都市;510300|自贡市;510400|攀枝花市;510500|泸州市;510600|德阳市;510700|绵阳市;510800|广元市;510900|遂宁市;511000|内江市;511100|乐山市;511300|南充市;511400|眉山市;511500|宜宾市;511600|广安市;511700|达州市;511800|雅安市;511900|巴中市;512000|资阳市;513200|阿坝州;513300|甘孜州;513400|凉山州
        520000|贵州省;520100|贵阳市;520200|六盘水市;520300|遵义市;520400|安顺市;520500|毕节市;520600|铜仁市;522300|黔西南州;522600|黔东南州;522700|黔南州
        530000|云南省;530100|昆明市;530300|曲靖市;530400|玉溪市;530500|保山市;530600|昭通市;530700|丽江市;530800|普洱市;530900|临沧市;532300|楚雄州;532500|红河州;532600|文山州;532800|西双版纳州;532900|大理州;533100|德宏州;533300|怒江州;533400|迪庆州
        540000|西藏自治区;540100|拉萨市;540200|日喀则市;540300|昌都市;540400|林芝市;540500|山南市;540600|那曲市
        610000|陕西省;610100|西安市;610200|铜川市;610300|宝鸡市;610400|咸阳市;610500|渭南市;610600|延安市;610700|汉中市;610800|榆林市;610900|安康市;611000|商洛市
        620000|甘肃省;620100|兰州市;620200|嘉峪关市;620300|金昌市;620400|白银市;620500|天水市;620600|武威市;620700|张掖市;620800|平凉市;620900|庆阳市;621000|定西市;621100|陇南市;622900|临夏州;623000|甘南州
        630000|青海省;630100|西宁市;630200|海东市
        640000|宁夏回族自治区;640100|银川市;640200|石嘴山市;640300|吴忠市;640400|固原市;640500|中卫市
        650000|新疆维吾尔自治区;650100|乌鲁木齐市;650200|克拉玛依市;650400|吐鲁番市;650500|哈密市
        710000|台湾省
        810000|香港特别行政区
        820000|澳门特别行政区
        """;

    /**
     * 省级（编码以 0000 结尾）
     */
    private static final Map<String, String> PROVINCES = new LinkedHashMap<>();

    /**
     * 市级（编码以 00 结尾且非省级）
     */
    private static final Map<String, String> CITIES = new LinkedHashMap<>();

    /**
     * 区县级
     */
    private static final Map<String, String> DISTRICTS = new LinkedHashMap<>();

    static {
        for (String line : StrUtil.split(StrUtil.trim(REGION_DATA), '\n')) {
            for (String item : StrUtil.splitTrim(line, ';')) {
                List<String> parts = StrUtil.split(item, '|');
                if (parts.size() != 2) {
                    continue;
                }
                String code = parts.get(0).trim();
                String name = parts.get(1).trim();
                if (code.endsWith("0000")) {
                    PROVINCES.put(code, name);
                } else if (code.endsWith("00")) {
                    CITIES.put(code, name);
                } else {
                    DISTRICTS.put(code, name);
                }
            }
        }
    }

    private RegionMatcher() {
    }

    /**
     * 省/自治区名称后缀（用于识别直辖市：省级以"市"结尾即视为直辖市）
     */
    private static final String MUNICIPALITY_SUFFIX = "市";

    /**
     * 从区划文本中尽力识别省/市/区县编码
     *
     * @param text 区划原文（可为地址全文）
     * @return 识别结果，未识别到的层级为 null
     */
    public static RegionHit match(String text) {
        String target = normalize(text);
        if (StrUtil.isEmpty(target)) {
            return new RegionHit(null, null, null);
        }
        String provinceCode = bestMatch(PROVINCES, target, 0);
        if (provinceCode == null) {
            // 地址经常只写到市（如"石家庄市桥西区示例路1号"、源站详情页的"失踪地址"也不带省名）。
            // 此前一律返回空，导致同一个人在候选库里裂成两条，正是去重要防的情况。
            // 这里在全部市级名称中找"唯一命中"：命中多个则放弃（不猜），避免把不同省的同名区县合并。
            String onlyCityCode = null;
            int cityHits = 0;
            for (Map.Entry<String, String> entry : CITIES.entrySet()) {
                for (String variant : nameVariants(entry.getValue())) {
                    if (target.contains(variant)) {
                        cityHits++;
                        onlyCityCode = entry.getKey();
                        break;
                    }
                }
            }
            if (cityHits == 1 && onlyCityCode != null) {
                String inferred = onlyCityCode.substring(0, 2) + "0000";
                if (PROVINCES.containsKey(inferred)) {
                    provinceCode = inferred;
                }
            }
        }
        if (provinceCode == null) {
            return new RegionHit(null, null, null);
        }
        String provincePrefix = provinceCode.substring(0, 2);
        String provinceName = PROVINCES.get(provinceCode);
        int searchFrom = endOf(target, provinceName);
        String cityCode;
        if (provinceName.endsWith(MUNICIPALITY_SUFFIX)) {
            // 直辖市：省级即市级，市辖区编码固定为"省前缀 + 0100"
            cityCode = provincePrefix + "0100";
        } else {
            cityCode = bestMatch(subIndex(CITIES, provincePrefix), target, searchFrom);
            if (cityCode != null) {
                searchFrom = endOf(target, CITIES.get(cityCode));
            }
        }
        String districtCode = bestMatch(subIndex(DISTRICTS, provincePrefix), target, searchFrom);
        return new RegionHit(provinceCode, cityCode, districtCode);
    }

    /**
     * 区划识别结果转展示文本（"河北省石家庄市"，未识别层级不补占位）
     *
     * @param hit 识别结果
     * @return 区划文本，全未识别时返回 null
     */
    public static String joinHit(RegionHit hit) {
        if (hit == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String code : new String[] {hit.provinceCode(), hit.cityCode(), hit.districtCode()}) {
            String name = nameOf(code);
            // 直辖市的省级与市级同名，只保留一次
            if (StrUtil.isNotBlank(name) && builder.indexOf(name) < 0) {
                builder.append(name);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    /**
     * 区划编码转内置名称
     *
     * @param code 区划编码
     * @return 名称，未收录时返回 null
     */
    public static String nameOf(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        String name = PROVINCES.get(code);
        if (name == null) {
            name = CITIES.get(code);
        }
        return name == null ? DISTRICTS.get(code) : name;
    }

    /**
     * 去掉行政级别后缀的简称（"河北省" → "河北"）
     *
     * @param name 全称
     * @return 简称（无需裁剪时返回原值）
     */
    public static String shortNameOf(String name) {
        if (StrUtil.isBlank(name)) {
            return name;
        }
        for (String variant : nameVariants(name)) {
            if (!variant.equals(name)) {
                return variant;
            }
        }
        return name;
    }

    /**
     * 去除空白与常见分隔符，避免"河 北 省 邯郸市"这类排版差异导致漏匹配
     */
    private static String normalize(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return StrUtil.cleanBlank(text).replace("，", "").replace(",", "").replace("　", "");
    }

    private static Map<String, String> subIndex(Map<String, String> index, String provincePrefix) {
        Map<String, String> result = new LinkedHashMap<>();
        index.forEach((code, name) -> {
            if (code.startsWith(provincePrefix)) {
                result.put(code, name);
            }
        });
        return result;
    }

    /**
     * 已匹配地名的结束位置（取各变体中最靠前的结束位置），未匹配到时返回 0
     */
    private static int endOf(String target, String name) {
        if (StrUtil.isBlank(name)) {
            return 0;
        }
        int end = Integer.MAX_VALUE;
        for (String variant : nameVariants(name)) {
            int index = target.indexOf(variant);
            if (index >= 0) {
                end = Math.min(end, index + variant.length());
            }
        }
        return end == Integer.MAX_VALUE ? 0 : end;
    }

    /**
     * 在文本中查找命中的地名（同层级取"出现位置最靠前、名称最长"者，避免把县名误判为市名）
     */
    private static String bestMatch(Map<String, String> index, String target, int fromIndex) {
        String hitCode = null;
        String hitName = null;
        int hitIndex = Integer.MAX_VALUE;
        int start = Math.max(fromIndex, 0);
        for (Map.Entry<String, String> entry : index.entrySet()) {
            for (String candidate : nameVariants(entry.getValue())) {
                int at = target.indexOf(candidate, start);
                if (at < 0) {
                    continue;
                }
                boolean better = at < hitIndex || (at == hitIndex && hitName != null && candidate.length() > hitName
                    .length());
                if (better) {
                    hitIndex = at;
                    hitName = candidate;
                    hitCode = entry.getKey();
                }
            }
        }
        return hitCode;
    }

    /**
     * 名称变体：全称 + 去掉行政级别后缀的简称（"河北省" ↔ "河北"、"邯郸市" ↔ "邯郸"）
     */
    private static List<String> nameVariants(String name) {
        List<String> variants = new ArrayList<>(2);
        variants.add(name);
        String shortName = name;
        for (String suffix : List.of("壮族自治区", "回族自治区", "维吾尔自治区", "特别行政区", "蒙古自治区", "自治区", "藏族", "州", "地区", "省", "市")) {
            if (shortName.endsWith(suffix) && shortName.length() > suffix.length() + 1) {
                shortName = shortName.substring(0, shortName.length() - suffix.length());
                break;
            }
        }
        if (!shortName.equals(name)) {
            variants.add(shortName);
        }
        return variants;
    }

    /**
     * 区划识别结果
     *
     * @param provinceCode 省编码
     * @param cityCode     市编码
     * @param districtCode 区县编码
     */
    public record RegionHit(String provinceCode, String cityCode, String districtCode) {
    }
}
