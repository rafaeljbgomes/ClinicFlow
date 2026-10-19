package com.clinicflow.clinical.adapters.inbound.rest;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class UnknownEndpointTest {
    @Test
    void returns404ForAnUnmappedApiEndpoint() throws Exception {
        // Arrange
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Object())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        // Act
        ResultActions response = mvc.perform(get("/unknown-resource"));

        // Assert
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("endpoint_not_found"));
    }
}
