package com.tosan.client.http.restclient.starter.configuration;

import com.tosan.client.http.core.HttpClientProperties;
import com.tosan.client.http.core.factory.ConfigurableApacheHttpClientFactory;
import com.tosan.client.http.restclient.starter.impl.ExternalServiceInvoker;
import com.tosan.client.http.restclient.starter.impl.interceptor.HttpLoggingInterceptor;
import com.tosan.client.http.restclient.starter.util.HttpLoggingInterceptorUtil;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
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

    private final HttpLoggingInterceptorUtil httpLoggingInterceptorUtil;

    protected AbstractHttpClientConfiguration(HttpLoggingInterceptorUtil httpLoggingInterceptorUtil) {
        this.httpLoggingInterceptorUtil = httpLoggingInterceptorUtil;
    }

    protected abstract String getExternalServiceName();

    protected abstract ResponseErrorHandler responseErrorHandler();

    protected RestClient restClient(HttpClientProperties httpClientProperties) {
        return this.restClient(
                httpMessageConverter(),
                clientHttpRequestFactory(
                        apacheHttpClientFactory(
                                apacheHttpClientBuilder(),
                                connectionManagerBuilder(),
                                httpClientProperties
                        )
                ),
                clientHttpRequestInterceptors(httpClientProperties,
                        new HttpLoggingInterceptor(this.httpLoggingInterceptorUtil, getExternalServiceName())),
                responseErrorHandler(), observationRegistry()
        );
    }

    protected ConfigurableApacheHttpClientFactory apacheHttpClientFactory(
            HttpClientBuilder builder,
            PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder,
            HttpClientProperties httpClientProperties) {
        return new ConfigurableApacheHttpClientFactory(builder, connectionManagerBuilder, httpClientProperties);
    }

    protected ClientHttpRequestFactory clientHttpRequestFactory(ConfigurableApacheHttpClientFactory apacheHttpClientFactory) {
        return new HttpComponentsClientHttpRequestFactory(apacheHttpClientFactory.createBuilder().build());
    }

    protected HttpClientBuilder apacheHttpClientBuilder() {
        return HttpClientBuilder.create();
    }

    protected PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder() {
        return PoolingHttpClientConnectionManagerBuilder.create();
    }

    protected HttpMessageConverter<?> httpMessageConverter() {
        return new JacksonJsonHttpMessageConverter();
    }

    protected ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    protected List<ClientHttpRequestInterceptor> clientHttpRequestInterceptors(
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

    protected RestClient restClient(
            HttpMessageConverter<?> httpMessageConverter,
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

    protected ExternalServiceInvoker serviceInvoker(Environment environment) {
        HttpClientProperties httpClientProperties = this.httpClientProperties(environment);
        return new ExternalServiceInvoker(this.restClient(httpClientProperties), httpClientProperties);
    }

    protected HttpClientProperties httpClientProperties(Environment environment) {
        HttpClientProperties props = new HttpClientProperties();
        Binder binder = Binder.get(environment);
        // TODO Perhaps we should consider changing the postfix
        binder.bind(getExternalServiceName() + ".client", Bindable.ofInstance(props));
        return props;
    }
}
