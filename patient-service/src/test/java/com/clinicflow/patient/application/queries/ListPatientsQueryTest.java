package com.clinicflow.patient.application.queries;

import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ListPatientsQueryTest {
    private static final PsychologistId OWNER = new PsychologistId(UUID.randomUUID());
    private static final PageQuery FIRST_PAGE = new PageQuery(0, PageQuery.DEFAULT_SIZE);

    @Test
    void absentFiltersMeanNoFilter() {
        // Act
        var query = new ListPatientsQuery(OWNER, null, null, null, null, null, FIRST_PAGE);

        // Assert
        assertThat(query.search()).isNull();
        assertThat(query.statuses()).isEmpty();
        assertThat(query.consentStatuses()).isEmpty();
        assertThat(query.createdFrom()).isNull();
        assertThat(query.ids()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void blankSearchMeansNoSearch(String search) {
        // Act
        var query = new ListPatientsQuery(OWNER, search, null, null, null, null, FIRST_PAGE);

        // Assert
        assertThat(query.search()).isNull();
    }

    @Test
    void searchIsStripped() {
        // Act
        var query = new ListPatientsQuery(OWNER, "  Ana Martins ", null, null, null, null, FIRST_PAGE);

        // Assert
        assertThat(query.search()).isEqualTo("Ana Martins");
    }

    @Test
    void searchMayUseTheWholeLimitAfterStripping() {
        // Arrange
        String search = " " + "a".repeat(ListPatientsQuery.MAX_SEARCH_LENGTH) + " ";

        // Act
        var query = new ListPatientsQuery(OWNER, search, null, null, null, null, FIRST_PAGE);

        // Assert
        assertThat(query.search()).hasSize(ListPatientsQuery.MAX_SEARCH_LENGTH);
    }

    @Test
    void searchLongerThanTheLimitIsRejected() {
        // Arrange
        String search = "a".repeat(ListPatientsQuery.MAX_SEARCH_LENGTH + 1);

        // Act + Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ListPatientsQuery(OWNER, search, null, null, null, null, FIRST_PAGE))
                .withMessageContaining("q must be at most");
    }

    @Test
    void lookupMayNameOnePageOfIds() {
        // Arrange
        Set<PatientId> ids = ids(PageQuery.MAX_SIZE);

        // Act
        var query = new ListPatientsQuery(OWNER, null, null, null, null, ids, FIRST_PAGE);

        // Assert
        assertThat(query.ids()).hasSize(PageQuery.MAX_SIZE);
    }

    @Test
    void lookupOfMoreThanOnePageOfIdsIsRejected() {
        // Arrange
        Set<PatientId> ids = ids(PageQuery.MAX_SIZE + 1);

        // Act + Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ListPatientsQuery(OWNER, null, null, null, null, ids, FIRST_PAGE))
                .withMessageContaining("ids");
    }

    @Test
    void filterSetsAreCopied() {
        // Arrange
        Set<PatientStatus> statuses = new HashSet<>(Set.of(PatientStatus.ACTIVE));
        Set<ConsentStatus> consent = new HashSet<>(Set.of(ConsentStatus.PENDING));
        Set<PatientId> ids = new HashSet<>(ids(1));
        var query = new ListPatientsQuery(OWNER, null, statuses, consent, null, ids, FIRST_PAGE);

        // Act
        statuses.add(PatientStatus.ARCHIVED);
        consent.add(ConsentStatus.REVOKED);
        ids.addAll(ids(2));

        // Assert
        assertThat(query.statuses()).containsExactly(PatientStatus.ACTIVE);
        assertThat(query.consentStatuses()).containsExactly(ConsentStatus.PENDING);
        assertThat(query.ids()).hasSize(1);
    }

    @Test
    void psychologistIsRequired() {
        // Act + Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ListPatientsQuery(null, null, null, null, null, null, FIRST_PAGE));
    }

    @Test
    void pageIsRequired() {
        // Act + Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ListPatientsQuery(OWNER, null, null, null, null, null, null));
    }

    @Test
    void pageQueryRejectsNegativePagesAndUnboundedSizes() {
        // Act + Assert
        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(-1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(0, PageQuery.MAX_SIZE + 1));
    }

    @Test
    void pageQueryAcceptsItsBounds() {
        // Act
        var smallest = new PageQuery(0, 1);
        var largest = new PageQuery(0, PageQuery.MAX_SIZE);

        // Assert
        assertThat(smallest.size()).isEqualTo(1);
        assertThat(largest.size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    private static Set<PatientId> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> new PatientId(UUID.randomUUID())).collect(Collectors.toSet());
    }
}
