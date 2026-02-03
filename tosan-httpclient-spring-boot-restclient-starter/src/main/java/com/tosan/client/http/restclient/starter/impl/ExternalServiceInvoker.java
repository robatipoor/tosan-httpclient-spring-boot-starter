package com.tosan.client.http.restclient.starter.impl;

import com.tosan.client.http.core.HttpClientProperties;
import org.springframework.web.client.RestClient;

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

    public String generateUrl(String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl + path.substring(1);
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}
