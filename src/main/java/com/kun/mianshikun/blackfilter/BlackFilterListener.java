package com.kun.mianshikun.blackfilter;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
@Slf4j
public class BlackFilterListener implements InitializingBean {
    @NacosInjected
    private ConfigService configService;
    @Value("${nacos.config.data-id}")
    private String dataId;
    @Value("${nacos.config.group}")
    private String group;

    private final Executor executorService = command -> new Thread(command).start();

    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            log.info("nacos 监听器启动");
            String config = configService.getConfigAndSignListener(dataId,
                    group, 5000, new Listener() {

                        @Override
                        public Executor getExecutor() {
                            return executorService;
                        }

                        @Override
                        public void receiveConfigInfo(String configInfo) {
                            log.info("监听到配置信息变化：{}", configInfo);
                            BlackListUtils.rebuild(configInfo);
                        }
                    });
            BlackListUtils.rebuild(config);
        } catch (Exception e) {
            BlackListUtils.rebuild("{}");
            log.error("Nacos 连接失败，黑名单功能不可用：{}", e);
        }
    }
}