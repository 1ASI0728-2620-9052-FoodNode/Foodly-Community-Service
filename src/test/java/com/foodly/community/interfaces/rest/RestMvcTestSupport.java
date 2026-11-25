package com.foodly.community.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** MVC aislado sin filtros: prueba transporte y handlers, no autenticación IAM. */
abstract class RestMvcTestSupport {

    private LocalValidatorFactoryBean validator;

    protected MockMvc mvc(Object... controllers) {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new RestExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @AfterEach
    void closeValidator() {
        if (validator != null) {
            validator.close();
        }
    }
}
