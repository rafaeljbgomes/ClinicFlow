package com.clinicflow.clinical.infrastructure.http;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ServiceClientConfig {
    @Bean
    RestClient patientServiceClient(RestClient.Builder builder, ServiceClientProperties properties) {
        return client(builder, properties.patient().baseUrl(), properties);
    }

    @Bean
    RestClient appointmentServiceClient(RestClient.Builder builder, ServiceClientProperties properties) {
        return client(builder, properties.appointment().baseUrl(), properties);
    }

    // Redirects are never followed so a forwarded bearer token cannot leave the configured service origin.
    private static RestClient client(RestClient.Builder builder, String baseUrl, ServiceClientProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout())
                .withRedirects(HttpRedirects.DONT_FOLLOW);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .build();
    }
}
