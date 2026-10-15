package com.clinicflow.notification.adapters.inbound.rest;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class UnknownEndpointTest {
    @Test
    void returns404ForAnUnmappedApiEndpoint() throws Exception {
        MockMvcBuilders.standaloneSetup(new Object())
                .setControllerAdvice(new ApiExceptionHandler())
                .build()
                .perform(get("/unknown-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("endpoint_not_found"));
    }
}
