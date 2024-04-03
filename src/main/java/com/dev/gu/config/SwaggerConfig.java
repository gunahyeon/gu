package com.dev.gu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI apiInfo() {
        Info apiInfo = new Info()
                .title("과제 - 썸네일")
                .description("Zip to Thumbnail")
                .version("1.0");

        return new OpenAPI()
                .info(apiInfo);
    }
}
