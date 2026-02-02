package com.tosan.client.http.restclient.starter.configuration;

import com.tosan.client.http.core.Constants;
import com.tosan.client.http.core.HttpClientProperties;
import com.tosan.client.http.core.factory.ConfigurableApacheHttpClientFactory;
import com.tosan.client.http.restclient.starter.impl.ExternalServiceInvoker;
import com.tosan.client.http.restclient.starter.impl.interceptor.HttpLoggingInterceptor;
import com.tosan.client.http.restclient.starter.util.HttpLoggingInterceptorUtil;
import com.tosan.tools.mask.starter.business.ComparisonTypeFactory;
import com.tosan.tools.mask.starter.business.ValueMaskFactory;
import com.tosan.tools.mask.starter.config.SecureParameter;
import com.tosan.tools.mask.starter.config.SecureParametersConfig;
import com.tosan.tools.mask.starter.configuration.MaskBeanConfiguration;
import com.tosan.tools.mask.starter.replace.JacksonReplaceHelper;
import com.tosan.tools.mask.starter.replace.JsonReplaceHelperDecider;
import com.tosan.tools.mask.starter.replace.RegexReplaceHelper;
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
import java.util.HashSet;
import java.util.List;

/**
 * @author Ali Alimohammadi
 * @since 8/3/2022
 */
public abstract class AbstractHttpClientConfiguration {

    public abstract String getExternalServiceName();

    public RestClient restClient(HttpClientProperties httpClientProperties) {
        return this.restClient(
                httpMessageConverter(),
                clientHttpRequestFactory(
                        apacheHttpClientFactory(
                                apacheHttpClientBuilder(),
                                connectionManagerBuilder(),
                                httpClientProperties
                        )
                ),
                clientHttpRequestInterceptors(httpClientProperties, httpLoggingRequestInterceptor(
                        httpLoggingInterceptorUtil(replaceHelperDecider(
                                jacksonReplaceHelper(),
                                regexReplaceHelper(),
                                secureParametersConfig()
                        )))),
                responseErrorHandler(), observationRegistry()
        );
    }

    public abstract JacksonReplaceHelper jacksonReplaceHelper() ;

    public abstract RegexReplaceHelper regexReplaceHelper() ;

    public JsonReplaceHelperDecider replaceHelperDecider(
            JacksonReplaceHelper jacksonReplaceHelper, RegexReplaceHelper regexReplaceHelper,
            SecureParametersConfig secureParametersConfig) {

        return new JsonReplaceHelperDecider(jacksonReplaceHelper, regexReplaceHelper, secureParametersConfig);
    }

    public SecureParametersConfig secureParametersConfig() {
        HashSet<SecureParameter> securedParameters = new HashSet<>(MaskBeanConfiguration.SECURED_PARAMETERS);
        securedParameters.add(Constants.AUTHORIZATION_SECURE_PARAM);
        securedParameters.add(Constants.PROXY_AUTHORIZATION_SECURE_PARAM);
        return new SecureParametersConfig(securedParameters);
    }

    public HttpLoggingInterceptorUtil httpLoggingInterceptorUtil(JsonReplaceHelperDecider replaceHelperDecider) {
        return new HttpLoggingInterceptorUtil(replaceHelperDecider);
    }

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

    public ExternalServiceInvoker serviceInvoker(Environment environment) {
        HttpClientProperties httpClientProperties = this.httpClientProperties(environment);
        return new ExternalServiceInvoker(this.restClient(httpClientProperties), httpClientProperties);
    }

    public HttpClientProperties httpClientProperties(Environment environment) {
        HttpClientProperties props = new HttpClientProperties();
        Binder binder = Binder.get(environment);
        binder.bind(getExternalServiceName() + ".client", Bindable.ofInstance(props));
        return props;
    }
}
