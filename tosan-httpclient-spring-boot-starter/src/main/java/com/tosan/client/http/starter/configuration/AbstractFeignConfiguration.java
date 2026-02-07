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
import feign.codec.ErrorDecoder;
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
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.tosan.client.http.core.Constants.*;

public abstract class AbstractFeignConfiguration implements DisposableBean {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AbstractFeignConfiguration.class);
    private final List<CloseableHttpClient> closeableHttpClients = Collections.synchronizedList(new ArrayList<>());
    private final ObjectMapper defaultObjectMapper = createDefaultObjectMapper();
    private final JsonReplaceHelperDecider jsonReplaceHelperDecider;

    protected AbstractFeignConfiguration(JsonReplaceHelperDecider jacksonReplaceHelper) {
        this.jsonReplaceHelperDecider = jacksonReplaceHelper;
    }

    protected abstract String getExternalServiceName();

    protected abstract CustomErrorDecoderConfig createCustomErrorDecoderConfig(ObjectMapper objectMapper);

    protected HttpClientProperties loadHttpClientProperties(Environment environment) {
        HttpClientProperties properties = new HttpClientProperties();
        Binder binder = Binder.get(environment);
        binder.bind(getExternalServiceName() + ".client", Bindable.ofInstance(properties));
        return properties;
    }

    protected ObjectMapper createObjectMapper() {
        return defaultObjectMapper;
    }

    protected Logger createLogger() {
        return new HttpFeignClientLogger(getExternalServiceName(), jsonReplaceHelperDecider);
    }

    protected Logger.Level getLogLevel() {
        return Logger.Level.FULL;
    }

    protected ObservationRegistry createObservationRegistry() {
        return ObservationRegistry.create();
    }

    protected CloseableHttpClient createFeignHttpClient(HttpClientProperties properties) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
                PoolingHttpClientConnectionManagerBuilder.create();

        ConfigurableApacheHttpClientFactory factory = new ConfigurableApacheHttpClientFactory(
                builder, connectionManagerBuilder, properties);

        // TODO: Implement proper resource management for CloseableHttpClient
        return factory.createBuilder().build();
    }

    protected Client wrapHttpClient(CloseableHttpClient closeableHttpClient) {
        closeableHttpClients.add(closeableHttpClient);
        return new ApacheHttp5Client(closeableHttpClient);
    }

    protected List<RequestInterceptor> createRequestInterceptors(HttpClientProperties properties) {
        List<RequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(createDefaultRequestInterceptor());
        if (properties.getAuthorization() != null && properties.getAuthorization().isEnable()) {
            interceptors.add(createBasicAuthInterceptor(properties));
        }
        return interceptors;
    }

    protected Encoder createEncoder(ObjectMapper objectMapper) {
        return new SpringFormEncoder(new JacksonEncoder(objectMapper));
    }

    protected Decoder createDecoder(ObjectMapper objectMapper) {
        return new ResponseEntityDecoder(new JacksonDecoder(objectMapper));
    }

    protected Contract createContract(ObjectMapper objectMapper) {
        return new SpringMvcContract();
    }

    protected ErrorDecoder createErrorDecoder(ObjectMapper objectMapper) {
        CustomErrorDecoderConfig config = createCustomErrorDecoderConfig(objectMapper);
        return new CustomErrorDecoder(config);
    }

    protected Retryer createRetryer() {
        return Retryer.NEVER_RETRY;
    }

    protected Request.Options createRequestOptions(HttpClientProperties properties) {
        HttpClientProperties.ConnectionConfiguration connectionConfig = properties.getConnection();
        return new Request.Options(
                connectionConfig.getConnectionTimeout(),
                TimeUnit.MILLISECONDS,
                connectionConfig.getSocketTimeout(),
                TimeUnit.MILLISECONDS,
                connectionConfig.isFollowRedirects()
        );
    }

    protected List<Capability> createCapabilities(ObservationRegistry observationRegistry) {
        TosanFeignObservationConvention convention = new TosanFeignObservationConvention()
                .externalName(getExternalServiceName());
        return List.of(new MicrometerObservationCapability(observationRegistry, convention));
    }

    protected Feign.Builder feignBuilder(HttpClientProperties httpClientProperties) {
        ObjectMapper objectMapper = createObjectMapper();
        Feign.Builder feignBuilder = Feign.builder()
                .client(wrapHttpClient(createFeignHttpClient(
                        httpClientProperties
                )))
                .options(createRequestOptions(httpClientProperties))
                .encoder(createEncoder(objectMapper))
                .decoder(createDecoder(objectMapper))
                .errorDecoder(createErrorDecoder(objectMapper))
                .contract(createContract(objectMapper))
                .requestInterceptors(createRequestInterceptors(httpClientProperties))
                .retryer(createRetryer())
                .logger(createLogger())
                .logLevel(getLogLevel());
        createCapabilities(createObservationRegistry()).forEach(feignBuilder::addCapability);
        return feignBuilder;
    }

    protected void validateProperties(HttpClientProperties properties) {
        if (properties.getBaseServiceUrl() == null) {
            throw new FeignConfigurationException(
                    "Base service URL for Feign client cannot be null for service: " + getExternalServiceName()
            );
        }
    }

    protected final RequestInterceptor createDefaultRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header(ACCEPT_HEADER, ContentType.APPLICATION_JSON.getMimeType());
            requestTemplate.header(CONTENT_TYPE_HEADER, ContentType.APPLICATION_JSON.getMimeType());
            addMdcHeaderIfPresent(requestTemplate, MDC_REQUEST_ID, X_REQUEST_ID);
            addMdcHeaderIfPresent(requestTemplate, MDC_CLIENT_IP, X_USER_IP);
        };
    }

    protected final void addMdcHeaderIfPresent(RequestTemplate requestTemplate, String mdcKey, String headerName) {
        String mdcValue = MDC.get(mdcKey);
        if (mdcValue != null) {
            requestTemplate.header(headerName, mdcValue);
        }
    }

    protected final RequestInterceptor createBasicAuthInterceptor(HttpClientProperties properties) {
        HttpClientProperties.AuthorizationConfiguration authConfig = properties.getAuthorization();
        return new BasicAuthRequestInterceptor(
                authConfig.getUsername(),
                authConfig.getPassword(),
                StandardCharsets.UTF_8
        );
    }

    private ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private String buildTargetUrl(HttpClientProperties properties, String controllerPath) {
        String baseUrl = properties.getBaseServiceUrl();
        return controllerPath != null ? baseUrl + controllerPath : baseUrl;
    }

    protected final <T> T createFeignClient(Environment environment, String controllerPath, Class<T> clientType) {
        HttpClientProperties properties = loadHttpClientProperties(environment);
        validateProperties(properties);
        Feign.Builder builder = feignBuilder(properties);
        return builder.target(clientType, buildTargetUrl(properties, controllerPath));
    }

    protected final <T> T createFeignClient(Environment environment, Class<T> clientType) {
        return createFeignClient(environment, null, clientType);
    }

    @Override
    public void destroy() {
        synchronized (closeableHttpClients) {
            closeableHttpClients.forEach(closeableHttpClient -> {
                LOG.info("Closing HTTP client connections for service: {}", getExternalServiceName());
                try {
                    closeableHttpClient.close();
                } catch (IOException e) {
                    LOG.error("Closing HTTP client connection failed ", e);
                }
            });
        }
    }
}
