package com.tosan.client.http.sample.restclient.config;

import com.tosan.client.http.restclient.starter.configuration.AbstractRestClientConfiguration;
import com.tosan.client.http.restclient.starter.impl.ExternalServiceInvoker;
import com.tosan.client.http.restclient.starter.util.HttpLoggingInterceptorUtil;
import com.tosan.client.http.sample.restclient.exception.ExceptionHandler;
import com.tosan.tools.mask.starter.replace.JsonReplaceHelperDecider;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * @author Ali Alimohammadi
 * @since 8/6/2022
 */
@Configuration
public class ExternalServiceConfiguration extends AbstractRestClientConfiguration {

    public static final String SERVICE_NAME = "custom-web-service1";

    public ExternalServiceConfiguration(RestClient.Builder builder, ObservationRegistry observationRegistry, JsonReplaceHelperDecider jacksonReplaceHelper) {
        super(builder, observationRegistry, jacksonReplaceHelper);
    }

    @Bean(SERVICE_NAME)
    public ExternalServiceInvoker serviceInvokerBean(Environment environment) {
        return super.createServiceInvoker(environment);
    }

    @Override
    protected String getExternalServiceName() {
        return SERVICE_NAME;
    }

    @Override
    protected ResponseErrorHandler createResponseErrorHandler() {
        return new ExceptionHandler();
    }
}
