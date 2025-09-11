package com.example.loan.api.config;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFeatureConfig {
    @PostConstruct
    public void registerProvider() {
        String host = System.getenv().getOrDefault("FLAGD_HOST", "flagd");
        int port = Integer.parseInt(System.getenv().getOrDefault("FLAGD_PORT", "8013"));
        FlagdOptions opts = FlagdOptions.builder().host(host).port(port).build();
        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(opts));
    }
}

