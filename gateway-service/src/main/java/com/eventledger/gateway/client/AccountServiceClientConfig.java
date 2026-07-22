package com.eventledger.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountServiceClientConfig {

    @Bean
    public RestClient accountServiceRestClient(@Value("${account-service.base-url}") String baseUrl,
            @Value("${account-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${account-service.read-timeout-ms}") int readTimeoutMs) {
        // SimpleClientHttpRequestFactory (HttpURLConnection-based) rather than the JDK HttpClient
        // default: the latter's HTTP/1.1 keep-alive handling raises spurious "EOF reached while
        // reading" errors against some servers (observed against WireMock's Jetty backend in tests).
        // A bounded read timeout here (rather than Resilience4j's async @TimeLimiter, which requires
        // CompletableFuture-returning methods) keeps this call synchronous and simple to run inside
        // the Gateway's JPA transaction, while still guaranteeing it can never block indefinitely.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
