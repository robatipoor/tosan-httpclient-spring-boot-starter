package com.tosan.client.http.restclient.starter.configuration;

import com.tosan.client.http.core.HttpClientProperties;
import com.tosan.client.http.core.factory.ConfigurableApacheHttpClientFactory;
import com.tosan.client.http.restclient.starter.impl.ExternalServiceInvoker;
import com.tosan.client.http.restclient.starter.impl.interceptor.HttpLoggingInterceptor;
import com.tosan.client.http.restclient.starter.util.HttpLoggingInterceptorUtil;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.http.converter.HttpMessageConverters;
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

    private final HttpLoggingInterceptorUtil httpLoggingInterceptorUtil;

    protected AbstractHttpClientConfiguration(HttpLoggingInterceptorUtil httpLoggingInterceptorUtil) {
        this.httpLoggingInterceptorUtil = httpLoggingInterceptorUtil;
    }

    protected abstract String getExternalServiceName();

    protected abstract ResponseErrorHandler createResponseErrorHandler();

    protected HttpClientProperties loadHttpClientProperties(Environment environment) {
        HttpClientProperties properties = new HttpClientProperties();
        Binder binder = Binder.get(environment);
        String propertyPrefix = getExternalServiceName() + ".client";
        binder.bind(propertyPrefix, Bindable.ofInstance(properties));
        return properties;
    }

    protected ObservationRegistry createObservationRegistry() {
        return ObservationRegistry.create();
    }

    protected TosanHttpClientObservationConvention createObservationConvention() {
        return new TosanHttpClientObservationConvention().externalName(getExternalServiceName());
    }

    protected RestClient createRestClient(HttpClientProperties properties) {
        return RestClient.builder()
                .configureMessageConverters(this::configureMessageConverters)
                .requestFactory(createRequestFactory(properties))
                .requestInterceptors(interceptors ->
                        interceptors.addAll(createInterceptors(properties)))
                .defaultStatusHandler(createResponseErrorHandler())
                .observationRegistry(createObservationRegistry())
                .observationConvention(createObservationConvention())
                .build();
    }

    protected CloseableHttpClient createHttpClient(HttpClientProperties properties) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
                PoolingHttpClientConnectionManagerBuilder.create();
        ConfigurableApacheHttpClientFactory factory = new ConfigurableApacheHttpClientFactory(
                builder, connectionManagerBuilder, properties);
        return factory.createBuilder().build();
    }

    protected ClientHttpRequestFactory createRequestFactory(HttpClientProperties properties) {
        CloseableHttpClient httpClient = createHttpClient(properties);
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    protected List<ClientHttpRequestInterceptor> createInterceptors(HttpClientProperties properties) {
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(createLoggingInterceptor());
        if (properties.getAuthorization().isEnable()) {
            interceptors.add(createBasicAuthInterceptor(properties));
        }
        return interceptors;
    }

    protected void configureMessageConverters(HttpMessageConverters.ClientBuilder converters) {
        converters.addCustomConverter(new JacksonJsonHttpMessageConverter());
    }

    protected final ClientHttpRequestInterceptor createLoggingInterceptor() {
        return new HttpLoggingInterceptor(httpLoggingInterceptorUtil, getExternalServiceName());
    }

    protected final ClientHttpRequestInterceptor createBasicAuthInterceptor(HttpClientProperties properties) {
        HttpClientProperties.AuthorizationConfiguration authConfig = properties.getAuthorization();
        return new BasicAuthenticationInterceptor(
                authConfig.getUsername(),
                authConfig.getPassword(),
                StandardCharsets.UTF_8
        );
    }

    protected final ExternalServiceInvoker createServiceInvoker(Environment environment) {
        HttpClientProperties properties = loadHttpClientProperties(environment);
        RestClient restClient = createRestClient(properties);
        return new ExternalServiceInvoker(restClient, properties);
    }
}
