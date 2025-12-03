package com.tosan.client.http.restclient.starter.configuration;

import com.tosan.client.http.core.HttpClientProperties;
import com.tosan.client.http.core.factory.ConfigurableApacheHttpClientFactory;
import com.tosan.client.http.restclient.starter.exception.RestClientConfigurationException;
import com.tosan.client.http.restclient.starter.impl.ExternalServiceInvoker;
import com.tosan.client.http.restclient.starter.impl.interceptor.HttpLoggingInterceptor;
import com.tosan.client.http.restclient.starter.util.HttpLoggingInterceptorUtil;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public abstract class AbstractHttpClientConfiguration implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHttpClientConfiguration.class);
    private final ObservationRegistry observationRegistry;
    private final List<CloseableHttpClient> closeableHttpClients = Collections.synchronizedList(new ArrayList<>());
    private final HttpLoggingInterceptorUtil httpLoggingInterceptorUtil;

    protected AbstractHttpClientConfiguration(ObservationRegistry observationRegistry, HttpLoggingInterceptorUtil httpLoggingInterceptorUtil) {
        this.observationRegistry = observationRegistry;
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

    protected void validateProperties(HttpClientProperties properties) {
        if (properties.getBaseServiceUrl() == null) {
            throw new RestClientConfigurationException(
                    "Base service URL for rest client cannot be null for service: " + getExternalServiceName()
            );
        }
    }

    protected DefaultClientRequestObservationConvention createObservationConvention() {
        return new TosanHttpClientObservationConvention().externalName(getExternalServiceName());
    }

    private RestClient createRestClient(HttpClientProperties properties) {
        return RestClient.builder()
                .configureMessageConverters(this::configureMessageConverters)
                .requestFactory(createRequestFactory(properties))
                .requestInterceptors(interceptors ->
                        interceptors.addAll(createInterceptors(properties)))
                .defaultStatusHandler(createResponseErrorHandler())
                .observationRegistry(observationRegistry)
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

    private ClientHttpRequestFactory createRequestFactory(HttpClientProperties properties) {
        CloseableHttpClient closeableHttpClient = createHttpClient(properties);
        closeableHttpClients.add(closeableHttpClient);
        return new HttpComponentsClientHttpRequestFactory(closeableHttpClient);
    }

    protected List<ClientHttpRequestInterceptor> createInterceptors(HttpClientProperties properties) {
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(createLoggingInterceptor());
        if (properties.getAuthorization() != null && properties.getAuthorization().isEnable()) {
            interceptors.add(createBasicAuthInterceptor(properties));
        }
        return interceptors;
    }

    protected void configureMessageConverters(HttpMessageConverters.ClientBuilder converters) {
        converters.addCustomConverter(new JacksonJsonHttpMessageConverter());
    }

    private ClientHttpRequestInterceptor createLoggingInterceptor() {
        return new HttpLoggingInterceptor(httpLoggingInterceptorUtil, getExternalServiceName());
    }

    private ClientHttpRequestInterceptor createBasicAuthInterceptor(HttpClientProperties properties) {
        HttpClientProperties.AuthorizationConfiguration authConfig = properties.getAuthorization();
        return new BasicAuthenticationInterceptor(
                authConfig.getUsername(),
                authConfig.getPassword(),
                StandardCharsets.UTF_8
        );
    }

    protected final ExternalServiceInvoker createServiceInvoker(Environment environment) {
        HttpClientProperties properties = loadHttpClientProperties(environment);
        validateProperties(properties);
        RestClient restClient = createRestClient(properties);
        return new ExternalServiceInvoker(restClient, properties);
    }

    @Override
    public void destroy() {
        synchronized (closeableHttpClients) {
            closeableHttpClients.forEach(closeableHttpClient -> {
                LOG.info("Closing HTTP client connections for service: {}", getExternalServiceName());
                try {
                    closeableHttpClient.close();
                } catch (IOException e) {
                    LOG.error("Failed to close the HTTP client connection", e);
                }
            });
        }
    }
}
