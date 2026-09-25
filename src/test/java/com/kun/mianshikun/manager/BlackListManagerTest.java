package com.kun.mianshikun.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.kun.mianshikun.blackfilter.BlackListUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 黑名单链路集成测试：封号 IP -> 本实例即时生效 -> 批量写入 Nacos -> 静态过滤器重建命中。
 * 注意：会在真实 Nacos 配置上做增删，测试结束会清理测试数据。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BlackListManagerTest {

    private static final String DATA_ID = "mianshikun-blackList";
    private static final String GROUP = "DEFAULT_GROUP";
    private static final String TEST_IP = "10.99.99.99";
    private static final String TEST_IP_2 = "10.99.99.98";
    private static final String EXISTING_IP = "1.1.1.1";

    @Resource
    private BlackListManager blackListManager;

    @NacosInjected
    private ConfigService configService;

    @Test
    @Order(1)
    @DisplayName("banIp 后本实例立即生效（无需等待 Nacos 推送）")
    void banIpTakesEffectImmediately() {
        assertFalse(blackListManager.isBlack(TEST_IP), "测试前该 IP 不应在黑名单中");
        blackListManager.banIp(TEST_IP);
        assertTrue(blackListManager.isBlack(TEST_IP), "封号后应立即命中本实例动态黑名单");
        assertFalse(blackListManager.isBlack("8.8.4.4"), "无关 IP 不应被误判");
        System.out.println("[1] 本地动态黑名单条数=" + blackListManager.localCount()
                + "，待同步=" + blackListManager.pendingCount());
    }

    @Test
    @Order(2)
    @DisplayName("flushNow 把封禁 IP 批量合并写入 Nacos 配置（不覆盖原有名单）")
    void flushWritesToNacos() throws Exception {
        blackListManager.banIp(TEST_IP_2);
        assertTrue(blackListManager.flushNow(), "同步到 Nacos 应成功");
        String content = configService.getConfig(DATA_ID, GROUP, 3000);
        assertNotNull(content, "Nacos 配置不应为空");
        assertTrue(content.contains(TEST_IP), "Nacos 配置应包含被封 IP：" + TEST_IP);
        assertTrue(content.contains(TEST_IP_2), "Nacos 配置应包含第二个被封 IP：" + TEST_IP_2);
        assertTrue(content.contains(EXISTING_IP), "原有名单不应被覆盖");
        System.out.println("[2] Nacos 配置 = " + content.replace("\r", "").replace("\n", " | "));
    }

    @Test
    @Order(3)
    @DisplayName("Guava 布隆过滤器重建后能命中静态名单")
    void rebuildUsesGuavaBloomFilter() {
        String config = "blackIpList:\n    - \"" + TEST_IP + "\"\n";
        BlackListUtils.rebuild(config);
        assertTrue(BlackListUtils.isBlack(TEST_IP), "重建后应命中静态名单");
        assertFalse(BlackListUtils.isBlack("8.8.8.8"), "未加入的 IP 不应命中（1% 误判率内）");
        System.out.println("[3] Guava BloomFilter 重建并命中校验通过");
    }

    @Test
    @Order(4)
    @DisplayName("解析与生成 YAML 格式正确（保持 blackIpList 结构）")
    void yamlRoundTrip() {
        List<String> parsed = BlackListUtils.parseIpList("blackIpList:\n    - \"1.1.1.1\"\n    - \"2.2.2.2\"\n");
        assertTrue(parsed.contains("1.1.1.1") && parsed.contains("2.2.2.2"), "应解析出两条 IP");
        String generated = BlackListUtils.toYaml(parsed);
        assertTrue(generated.contains("blackIpList:") && generated.contains("- \"1.1.1.1\""),
                "生成的 YAML 应保持原格式：" + generated);
        assertTrue(BlackListUtils.parseIpList(BlackListUtils.toYaml(new ArrayList<>())).isEmpty(),
                "空名单应可正常解析");
        System.out.println("[4] YAML 往返校验通过");
    }

    @Test
    @Order(5)
    @DisplayName("清理测试数据并恢复静态过滤器")
    void cleanup() throws Exception {
        String content = configService.getConfig(DATA_ID, GROUP, 3000);
        List<String> list = new ArrayList<>(BlackListUtils.parseIpList(content));
        list.remove(TEST_IP);
        list.remove(TEST_IP_2);
        assertTrue(configService.publishConfig(DATA_ID, GROUP, BlackListUtils.toYaml(list),
                ConfigType.YAML.getType()), "清理写入应成功");
        String after = configService.getConfig(DATA_ID, GROUP, 3000);
        assertFalse(after.contains(TEST_IP), "测试 IP 应已从 Nacos 移除");
        assertTrue(after.contains(EXISTING_IP), "原有名单应保留");
        BlackListUtils.rebuild(after);
        System.out.println("[5] 清理完成，配置 = " + after.replace("\r", "").replace("\n", " | "));
    }
}
