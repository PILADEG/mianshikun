package com.kun.mianshikun.blackfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

// 黑名单布隆过滤器工具
@Slf4j
public class BlackListUtils {

    /** 默认预期容量（静态名单规模可控） */
    private static final int DEFAULT_EXPECTED_SIZE = 10_000;
    /** 可接受的误判率 */
    private static final double DEFAULT_FPP = 0.01;
    /** YAML 中的名单字段名 */
    private static final String FIELD_NAME = "blackIpList";

    /**
     * volatile 保证 rebuild 后的新实例对其它线程立即可见
     * （原实现为非 volatile 静态字段，存在可见性问题：旧实例可能被长期"看见"而无法回收）
     */
    private static volatile BloomFilter<String> bloomFilter = create(DEFAULT_EXPECTED_SIZE);

    private BlackListUtils() {
    }

    private static BloomFilter<String> create(int expectedSize) {
        return BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8),
                Math.max(1024, expectedSize), DEFAULT_FPP);
    }

    /** 判断 IP 是否命中静态黑名单 */
    public static boolean isBlack(String ip) {
        BloomFilter<String> snapshot = bloomFilter;
        return snapshot != null && StringUtils.isNotBlank(ip) && snapshot.mightContain(ip);
    }

    /** 解析 YAML 配置中的 blackIpList */
    @SuppressWarnings("unchecked")
    public static List<String> parseIpList(String config) {
        if (StringUtils.isBlank(config)) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> map = new Yaml().loadAs(config, Map.class);
            if (map == null) {
                return Collections.emptyList();
            }
            Object value = map.get(FIELD_NAME);
            if (!(value instanceof List)) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("解析黑名单配置失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 生成 YAML 配置内容（保持 blackIpList 格式） */
    public static String toYaml(List<String> ipList) {
        if (ipList == null || ipList.isEmpty()) {
            return FIELD_NAME + ": []\n";
        }
        StringBuilder sb = new StringBuilder(FIELD_NAME).append(":\n");
        for (String ip : ipList) {
            sb.append("    - \"").append(ip).append("\"\n");
        }
        return sb.toString();
    }

    /** 全量重建：先构建新实例，再原子替换引用（避免中间态被读到） */
    public static void rebuild(String config) {
        List<String> blackList = parseIpList(config);
        BloomFilter<String> fresh = create(blackList.size());
        for (String ip : blackList) {
            fresh.put(ip);
        }
        bloomFilter = fresh;
        log.info("黑名单重建完成：静态名单 {} 条（误判率上限 {}）", blackList.size(), DEFAULT_FPP);
    }
}
