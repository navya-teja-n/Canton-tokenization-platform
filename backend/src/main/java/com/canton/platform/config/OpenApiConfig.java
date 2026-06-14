package com.canton.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger / OpenAPI documentation for the REST surface. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Institutional Tokenization & Deposit Network API")
                        .description("REST API for tokenized bonds, treasuries, deposits, "
                                + "trades, repos and KYC-gated custodial wallets, "
                                + "backed by a Canton/DAML Ledger API simulation.")
                        .version("v0.1.0")
                        .contact(new Contact().name("Platform Engineering")));
    }
}
