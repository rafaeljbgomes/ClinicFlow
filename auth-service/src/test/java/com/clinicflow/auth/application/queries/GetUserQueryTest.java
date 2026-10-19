package com.clinicflow.auth.application.queries;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class GetUserQueryTest {
    @Test
    void rejectsMissingUserId() {
        // Act
        Throwable thrown = catchThrowable(() -> new GetUserQuery(null));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User id is required");
    }
}
