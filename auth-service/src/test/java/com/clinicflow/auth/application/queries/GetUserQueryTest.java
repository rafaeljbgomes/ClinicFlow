package com.clinicflow.auth.application.queries;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetUserQueryTest {
    @Test
    void rejectsMissingUserId() {
        assertThatThrownBy(() -> new GetUserQuery(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User id is required");
    }
}
