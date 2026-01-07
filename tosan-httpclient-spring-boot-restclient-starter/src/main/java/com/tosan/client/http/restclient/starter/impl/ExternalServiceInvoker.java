package com.tosan.client.http.restclient.starter.impl;

import com.tosan.client.http.core.HttpClientProperties;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * @author Ali Alimohammadi
 * @since 8/6/2022
 */
public class ExternalServiceInvoker {
    private final String baseUrl;
    private final RestClient restClient;

    public ExternalServiceInvoker(RestClient restClient, HttpClientProperties httpClientProperties) {
        this.restClient = restClient;
        baseUrl = httpClientProperties.getBaseServiceUrl();
    }

    public RestClient getRestClient() {
        return this.restClient;
    }

    public String generateUrl(String url) {
        return baseUrl + url;
    }
}
