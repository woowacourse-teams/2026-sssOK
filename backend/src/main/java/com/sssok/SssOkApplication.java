package com.sssok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SssOkApplication {

    public static void main(String[] args) {
        SpringApplication.run(SssOkApplication.class, args);
    }

}
