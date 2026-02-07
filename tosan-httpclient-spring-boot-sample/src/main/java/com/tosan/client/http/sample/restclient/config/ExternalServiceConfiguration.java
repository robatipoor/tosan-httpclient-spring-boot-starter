package com.tosan.client.http.sample.restclient.config;

import com.tosan.client.http.restclient.starter.configuration.AbstractHttpClientConfiguration;
import com.tosan.client.http.restclient.starter.impl.ExternalServiceInvoker;
import com.tosan.client.http.restclient.starter.util.HttpLoggingInterceptorUtil;
import com.tosan.client.http.sample.restclient.exception.ExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * @author Ali Alimohammadi
 * @since 8/6/2022
 */
@Configuration
public class ExternalServiceConfiguration extends AbstractHttpClientConfiguration {

    public static final String SERVICE_NAME = "custom-web-service1";

    public ExternalServiceConfiguration(HttpLoggingInterceptorUtil httpLoggingInterceptorUtil) {
        super(httpLoggingInterceptorUtil);
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
