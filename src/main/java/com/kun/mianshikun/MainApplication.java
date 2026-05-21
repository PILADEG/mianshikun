package com.kun.mianshikun;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 主类（项目启动入口）
 *
 * @author <a href="https://github.com/likun">程序员鱼皮</a>
 * @from <a href="https://kun.icu">编程导航知识星球</a>
 */
@SpringBootApplication
@MapperScan("com.kun.mianshikun.mapper")
@EnableScheduling
@EnableTransactionManagement
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class MainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

}
