package com.tosan.client.http.sample.server.api.config.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tosan.client.http.sample.server.api.controller.CustomServerRestController;
import com.tosan.client.http.sample.server.api.exception.CustomServerException;
import com.tosan.client.http.starter.configuration.AbstractFeignConfiguration;
import com.tosan.client.http.starter.impl.feign.CustomErrorDecoderConfig;
import com.tosan.client.http.starter.impl.feign.ExceptionExtractType;
import com.tosan.client.http.starter.impl.feign.exception.TosanWebServiceRuntimeException;
import com.tosan.tools.mask.starter.config.SecureParametersConfig;
import com.tosan.tools.mask.starter.replace.JacksonReplaceHelper;
import com.tosan.tools.mask.starter.replace.JsonReplaceHelperDecider;
import com.tosan.tools.mask.starter.replace.RegexReplaceHelper;
import feign.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * @author Ali Alimohammadi
 * @since 4/18/2021
 */
@Configuration
@EnableFeignClients
@Slf4j
public class CustomServerFeignConfig extends AbstractFeignConfiguration {
    public static final String SERVICE_NAME = "custom-web-service2";

    public CustomServerFeignConfig(JsonReplaceHelperDecider jsonReplaceHelperDecider) {
        super(jsonReplaceHelperDecider);
    }

    @Override
    public String getExternalServiceName() {
        return SERVICE_NAME;
    }

    @Bean(SERVICE_NAME)
    public CustomServerRestController customServerRestControllerBean(Environment environment) {
        return getFeignController(environment, CustomServerRestController.PATH, CustomServerRestController.class);
    }

    @Override
    public CustomErrorDecoderConfig customErrorDecoderConfig(ObjectMapper objectMapper) {
        CustomErrorDecoderConfig customErrorDecoderConfig = new CustomErrorDecoderConfig();
        customErrorDecoderConfig.getScanPackageList().add("com.tosan.client.http.sample.server.api.exception");
        customErrorDecoderConfig.setExceptionExtractType(ExceptionExtractType.EXCEPTION_IDENTIFIER_FIELDS);
        customErrorDecoderConfig.setCheckedExceptionClass(CustomServerException.class);
        customErrorDecoderConfig.setUncheckedExceptionClass(TosanWebServiceRuntimeException.class);
        customErrorDecoderConfig.setObjectMapper(objectMapper);
        return customErrorDecoderConfig;
    }
}
