package com.clinicflow.appointment.infrastructure.http;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ServiceClientConfig {
    // Redirects are never followed so a forwarded bearer token cannot leave the configured service origin.
    @Bean
    RestClient patientServiceClient(RestClient.Builder builder, ServiceClientProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout())
                .withRedirects(HttpRedirects.DONT_FOLLOW);
        return builder.clone()
                .baseUrl(properties.patient().baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .build();
    }
}
