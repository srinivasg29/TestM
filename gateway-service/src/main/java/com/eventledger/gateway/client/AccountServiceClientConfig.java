package com.eventledger.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountServiceClientConfig {

    @Bean
    public RestClient accountServiceRestClient(@Value("${account-service.base-url}") String baseUrl) {
        // SimpleClientHttpRequestFactory (HttpURLConnection-based) rather than the JDK HttpClient
        // default: the latter's HTTP/1.1 keep-alive handling raises spurious "EOF reached while
        // reading" errors against some servers (observed against WireMock's Jetty backend in tests).
        return RestClient.builder().baseUrl(baseUrl).requestFactory(new SimpleClientHttpRequestFactory()).build();
    }
}
