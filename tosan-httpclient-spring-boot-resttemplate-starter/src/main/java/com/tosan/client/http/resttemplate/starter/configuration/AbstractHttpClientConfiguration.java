package com.tosan.client.http.resttemplate.starter.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tosan.client.http.core.HttpClientProperties;
import com.tosan.client.http.core.factory.ConfigurableApacheHttpClientFactory;
import com.tosan.client.http.resttemplate.starter.impl.ExternalServiceInvoker;
import com.tosan.client.http.resttemplate.starter.impl.interceptor.HttpLoggingInterceptor;
import com.tosan.client.http.resttemplate.starter.util.HttpLoggingInterceptorUtil;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ali Alimohammadi
 * @since 8/3/2022
 */
public abstract class AbstractHttpClientConfiguration {

    public abstract String getExternalServiceName();

    public abstract HttpClientProperties clientConfig();

    public ConfigurableApacheHttpClientFactory apacheHttpClientFactory(
            HttpClientBuilder builder,
            PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder,
            HttpClientProperties httpClientProperties) {
        return new ConfigurableApacheHttpClientFactory(builder, connectionManagerBuilder, httpClientProperties);
    }

    public ClientHttpRequestFactory clientHttpRequestFactory(ConfigurableApacheHttpClientFactory apacheHttpClientFactory) {
        return new HttpComponentsClientHttpRequestFactory(apacheHttpClientFactory.createBuilder().build());
    }

    public HttpClientBuilder apacheHttpClientBuilder() {
        return HttpClientBuilder.create();
    }

    public PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder() {
        return PoolingHttpClientConnectionManagerBuilder.create();
    }

    public HttpMessageConverter<Object> httpMessageConverter() {
        return new JacksonJsonHttpMessageConverter();
    }

    public ClientHttpRequestInterceptor httpLoggingRequestInterceptor(HttpLoggingInterceptorUtil httpLoggingInterceptorUtil) {
        return new HttpLoggingInterceptor(httpLoggingInterceptorUtil, getExternalServiceName());
    }

    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    public List<ClientHttpRequestInterceptor> clientHttpRequestInterceptors(
            HttpClientProperties httpClientProperties,
            ClientHttpRequestInterceptor httpLoggingRequestInterceptor) {
        List<ClientHttpRequestInterceptor> clientHttpRequestInterceptors = new ArrayList<>();
        clientHttpRequestInterceptors.add(httpLoggingRequestInterceptor);
        HttpClientProperties.AuthorizationConfiguration authorizationConfiguration =
                httpClientProperties.getAuthorization();
        if (httpClientProperties.getAuthorization().isEnable()) {
            clientHttpRequestInterceptors.add(new BasicAuthenticationInterceptor(authorizationConfiguration.getUsername(),
                    authorizationConfiguration.getPassword(), StandardCharsets.UTF_8));
        }
        return clientHttpRequestInterceptors;
    }

    public abstract ResponseErrorHandler responseErrorHandler();

    public RestClient restClient(
            HttpMessageConverter<Object> httpMessageConverter,
            ClientHttpRequestFactory clientHttpRequestFactory,
            List<ClientHttpRequestInterceptor> clientHttpRequestInterceptors,
            ResponseErrorHandler responseErrorHandler,
            ObservationRegistry observationRegistry) {
        return RestClient.builder().configureMessageConverters(converters -> {
                    converters.addCustomConverter(httpMessageConverter);
                })
                .requestFactory(clientHttpRequestFactory)
                .requestInterceptors(interceptors -> {
                    interceptors.addAll(clientHttpRequestInterceptors);
                })
                .defaultStatusHandler(responseErrorHandler)
                .observationRegistry(observationRegistry)
                .observationConvention(new TosanHttpClientObservationConvention().externalName(getExternalServiceName()))
                .build();
    }

    public ExternalServiceInvoker serviceInvoker(RestClient restClient, HttpClientProperties httpClientProperties) {
        return new ExternalServiceInvoker(restClient, httpClientProperties);
    }
}
