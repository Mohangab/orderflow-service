package com.mohan.orderflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderflowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("OrderFlow Service API")
                .version("1.0.0")
                .description("""
                        Event-driven order processing. Orders are created as PENDING, an OrderCreated \
                        event is relayed to Kafka through a transactional outbox, and a processing \
                        consumer (inventory + payment simulator) moves them to CONFIRMED or REJECTED.""")
                .contact(new Contact().name("Mohan Venkatesh Kola").url("https://github.com/Mohangab"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
