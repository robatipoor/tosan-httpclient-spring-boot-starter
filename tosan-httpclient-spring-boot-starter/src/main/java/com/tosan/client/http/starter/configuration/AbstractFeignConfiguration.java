package com.tosan.client.http.starter.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tosan.client.http.core.HttpClientProperties;
import com.tosan.client.http.core.factory.ConfigurableApacheHttpClientFactory;
import com.tosan.client.http.starter.impl.feign.CustomErrorDecoder;
import com.tosan.client.http.starter.impl.feign.CustomErrorDecoderConfig;
import com.tosan.client.http.starter.impl.feign.exception.FeignConfigurationException;
import com.tosan.client.http.starter.impl.feign.logger.HttpFeignClientLogger;
import com.tosan.tools.mask.starter.replace.JsonReplaceHelperDecider;
import feign.*;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import feign.hc5.ApacheHttp5Client;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.micrometer.MicrometerObservationCapability;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.tosan.client.http.core.Constants.*;

/**
 * @author Ali Alimohammadi
 * @since 7/19/2022
 */
public abstract class AbstractFeignConfiguration {

    private final JsonReplaceHelperDecider jsonReplaceHelperDecider;

    protected abstract String getExternalServiceName();

    protected abstract CustomErrorDecoderConfig customErrorDecoderConfig(ObjectMapper objectMapper);

    protected ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return objectMapper;
    }

    protected AbstractFeignConfiguration(JsonReplaceHelperDecider jacksonReplaceHelper) {
        this.jsonReplaceHelperDecider = jacksonReplaceHelper;
    }

    protected Logger httpFeignClientLogger(JsonReplaceHelperDecider replaceHelperDecider) {
        return new HttpFeignClientLogger(getExternalServiceName(), replaceHelperDecider);
    }

    protected ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    protected ConfigurableApacheHttpClientFactory apacheHttpClientFactory(
            HttpClientBuilder builder,
            PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder,
            HttpClientProperties httpClientProperties) {
        return new ConfigurableApacheHttpClientFactory(builder, connectionManagerBuilder, httpClientProperties);
    }

    protected CloseableHttpClient httpClient(ConfigurableApacheHttpClientFactory apacheHttpClientFactory) {
        //todo: closeable
        return apacheHttpClientFactory.createBuilder().build();
    }

    protected PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder() {
        return PoolingHttpClientConnectionManagerBuilder.create();
    }

    protected Client feignClient(HttpClient httpClient) {
        return new ApacheHttp5Client(httpClient);
    }

    protected RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header(ACCEPT_HEADER, ContentType.APPLICATION_JSON.getMimeType());
            requestTemplate.header(CONTENT_TYPE_HEADER, ContentType.APPLICATION_JSON.getMimeType());
            if (MDC.get(MDC_REQUEST_ID) != null) {
                requestTemplate.header(X_REQUEST_ID, MDC.get(MDC_REQUEST_ID));
            }
            if (MDC.get(MDC_CLIENT_IP) != null) {
                requestTemplate.header(X_USER_IP, MDC.get(MDC_CLIENT_IP));
            }
        };
    }

    protected List<RequestInterceptor> requestInterceptors(HttpClientProperties customServerClientConfig,
                                                           RequestInterceptor requestInterceptor) {
        List<RequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(requestInterceptor);
        HttpClientProperties.AuthorizationConfiguration authorizationConfiguration =
                customServerClientConfig.getAuthorization();
        if (customServerClientConfig.getAuthorization().isEnable()) {
            requestInterceptors.add(new BasicAuthRequestInterceptor(authorizationConfiguration.getUsername(),
                    authorizationConfiguration.getPassword(), StandardCharsets.UTF_8));
        }
        return requestInterceptors;
    }

    protected Contract contract() {
        return new SpringMvcContract();
    }

    protected Encoder encoder(ObjectMapper objectMapper) {
        return new SpringFormEncoder(new JacksonEncoder(objectMapper));
    }

    protected Decoder decoder(ObjectMapper objectMapper) {
        return new ResponseEntityDecoder(new JacksonDecoder(objectMapper));
    }

    protected CustomErrorDecoder customErrorDecoder(CustomErrorDecoderConfig customErrorDecoderConfig) {
        return new CustomErrorDecoder(customErrorDecoderConfig);
    }

    protected HttpClientBuilder httpClientBuilder() {
        return HttpClientBuilder.create();
    }

    protected Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    protected Logger.Level loggerLevel() {
        return Logger.Level.FULL;
    }

    protected HttpClientProperties httpClientProperties(Environment environment) {
        HttpClientProperties props = new HttpClientProperties();
        Binder binder = Binder.get(environment);
        // TODO Perhaps we should consider changing the postfix
        binder.bind(getExternalServiceName() + ".client", Bindable.ofInstance(props));
        return props;
    }

    protected Request.Options requestOptions(HttpClientProperties customServerClientConfig) {
        HttpClientProperties.ConnectionConfiguration connectionConfiguration = customServerClientConfig
                .getConnection();
        return new Request.Options(
                connectionConfiguration.getConnectionTimeout(), TimeUnit.MILLISECONDS,
                connectionConfiguration.getSocketTimeout(), TimeUnit.MILLISECONDS, connectionConfiguration
                .isFollowRedirects());
    }

    protected List<Capability> capabilities(ObservationRegistry observationRegistry) {
        return List.of(new MicrometerObservationCapability(observationRegistry,
                new TosanFeignObservationConvention().externalName(getExternalServiceName())));
    }

    protected Feign.Builder feignBuilder(HttpClientProperties httpClientProperties) {
        ObjectMapper objectMapper = objectMapper();
        Feign.Builder feignBuilder = Feign.builder().client(feignClient(httpClient(
                        apacheHttpClientFactory(
                                httpClientBuilder(),
                                connectionManagerBuilder(),
                                httpClientProperties
                        )
                )))
                .options(requestOptions(httpClientProperties))
                .encoder(encoder(objectMapper))
                .decoder(decoder(objectMapper))
                .errorDecoder(customErrorDecoder(customErrorDecoderConfig(objectMapper)))
                .contract(contract())
                .requestInterceptors(requestInterceptors(httpClientProperties, requestInterceptor()))
                .retryer(retryer())
                .logger(httpFeignClientLogger(jsonReplaceHelperDecider))
                .logLevel(loggerLevel());
        capabilities(observationRegistry()).forEach(feignBuilder::addCapability);
        return feignBuilder;
    }

    protected final <T> T getFeignController(Environment environment, String controllerPath, Class<T> classType) {
        HttpClientProperties httpClientProperties = httpClientProperties(environment);
        String baseServiceUrl = httpClientProperties.getBaseServiceUrl();
        if (baseServiceUrl == null) {
            throw new FeignConfigurationException("base service url for feign client can not be null.");
        }
        return feignBuilder(httpClientProperties).target(classType, baseServiceUrl + controllerPath);
    }

    protected final <T> T getFeignController(Environment environment, Class<T> classType) {
        return getFeignController(environment, null, classType);
    }
}
