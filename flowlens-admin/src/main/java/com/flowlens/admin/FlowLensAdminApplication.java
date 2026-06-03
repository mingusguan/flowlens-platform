package com.flowlens.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.flowlens.admin.**.mapper")
@EnableScheduling
@SpringBootApplication
public class FlowLensAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowLensAdminApplication.class, args);
    }
}
