package com.kun.mianshikun.config;

import com.jd.platform.hotkey.client.ClientStarter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * JD HotKey 热 key 探测客户端配置
 * <p>
 * 前置条件：
 * 1. etcd、Worker、Dashboard 已启动
 * 2. hotkey-client jar 已放入 lib 目录
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.hotkey")
@Slf4j
public class HotkeyClientConfig {

    private String appName;

    private String etcdServer;

    private Integer caffeineSize = 10000;

    private Long pushPeriod = 1000L;
    @PostConstruct
    public void init() {
        log.info("HotKey 客户端开始初始化, appName={}, etcdServer={}, caffeineSize={}, pushPeriod={}",
                appName, etcdServer, caffeineSize, pushPeriod);
        try {
            ClientStarter starter = new ClientStarter.Builder()
                    .setAppName(appName)
                    .setEtcdServer(etcdServer)
                    .setCaffeineSize(caffeineSize)
                    .setPushPeriod(pushPeriod)
                    .build();
            starter.startPipeline();
            log.info("HotKey 客户端初始化完成");
        } catch (Exception e) {
            log.error("HotKey 客户端初始化失败，不影响应用启动", e);
        }
    }
}
