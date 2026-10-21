package io.kestra.core.repositories;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.searchable.SearchableOutput;
import io.kestra.core.searchable.SearchableOutputRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(transactional = false)
public abstract class AbstractSearchableOutputRepositoryTest {
    @Inject
    protected SearchableOutputRepositoryInterface searchableOutputRepository;

    private Execution execution(String tenant) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace("io.kestra.test")
            .flowId("flow")
            .build();
    }

    @Test
    void shouldSaveSearchableOutput() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var output = new SearchableOutput(tenant, execution.getId(), "output", "value");

        searchableOutputRepository.save(output);

        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "output")).contains(output);
    }

    @Test
    void shouldSaveSearchableOutputWithNullValue() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var output = new SearchableOutput(tenant, execution.getId(), "output", null);

        searchableOutputRepository.save(output);

        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "output")).contains(output);
    }

    @Test
    void shouldSaveLongSearchableOutputValue() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var output = new SearchableOutput(tenant, execution.getId(), "output", "a".repeat(10_000));

        searchableOutputRepository.save(output);

        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "output")).contains(output);
    }

    @Test
    void shouldUpdateExistingSearchableOutput() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, execution.getId(), "output", "initial"));
        var updated = new SearchableOutput(tenant, execution.getId(), "output", "updated");

        searchableOutputRepository.save(updated);

        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "output")).contains(updated);
        assertThat(searchableOutputRepository.purge(List.of(execution))).isEqualTo(1);
    }

    @Test
    void shouldFindSearchableOutputById() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var first = new SearchableOutput(tenant, execution.getId(), "first", "value1");
        var second = new SearchableOutput(tenant, execution.getId(), "second", "value2");
        searchableOutputRepository.save(first);
        searchableOutputRepository.save(second);

        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "first")).contains(first);
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "second")).contains(second);
    }

    @Test
    void shouldReturnEmptyWhenSearchableOutputIsNotFound() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        assertThat(searchableOutputRepository.findById(tenant, IdUtils.create(), "output")).isEmpty();
    }

    @Test
    void shouldFindSearchableOutputOfTheGivenTenantAndExecutionOnly() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, execution.getId(), "output", "value"));

        assertThat(searchableOutputRepository.findById(TestsUtils.randomTenant(this.getClass().getSimpleName()), execution.getId(), "output")).isEmpty();
        assertThat(searchableOutputRepository.findById(tenant, execution(tenant).getId(), "output")).isEmpty();
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "other")).isEmpty();
    }

    @Test
    void shouldPurgeAllSearchableOutputsOfTheGivenExecutions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution1 = execution(tenant);
        Execution execution2 = execution(tenant);
        Execution untouched = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, execution1.getId(), "first", "value"));
        searchableOutputRepository.save(new SearchableOutput(tenant, execution1.getId(), "second", "value"));
        searchableOutputRepository.save(new SearchableOutput(tenant, execution2.getId(), "first", "value"));
        searchableOutputRepository.save(new SearchableOutput(tenant, untouched.getId(), "first", "value"));

        int purged = searchableOutputRepository.purge(List.of(execution1, execution2));

        assertThat(purged).isEqualTo(3);
        assertThat(searchableOutputRepository.findById(tenant, execution1.getId(), "first")).isEmpty();
        assertThat(searchableOutputRepository.findById(tenant, execution1.getId(), "second")).isEmpty();
        assertThat(searchableOutputRepository.findById(tenant, execution2.getId(), "first")).isEmpty();
        assertThat(searchableOutputRepository.findById(tenant, untouched.getId(), "first")).isPresent();
    }

    @Test
    void shouldPurgeNothingWhenExecutionHasNoSearchableOutput() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        assertThat(searchableOutputRepository.purge(List.of(execution(tenant)))).isZero();
    }

    @Test
    void shouldFindExecutionIdsByOutputValue() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution exact = execution(tenant);
        Execution otherValue = execution(tenant);
        Execution otherOutputId = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, exact.getId(), "orderIds", "ORD-8841"));
        searchableOutputRepository.save(new SearchableOutput(tenant, otherValue.getId(), "orderIds", "ORD-8841-bis"));
        searchableOutputRepository.save(new SearchableOutput(tenant, otherOutputId.getId(), "other", "ORD-8841"));

        assertThat(searchableOutputRepository.findExecutionIds(tenant, "orderIds", Op.EQUALS, "ORD-8841", 10))
            .containsExactly(exact.getId());
    }

    @Test
    void shouldMatchOutputValueCaseSensitively() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, execution.getId(), "output", "Value_1"));

        assertThat(searchableOutputRepository.findExecutionIds(tenant, "output", Op.EQUALS, "value_1", 10)).isEmpty();
        assertThat(searchableOutputRepository.findExecutionIds(tenant, "output", Op.EQUALS, "Value_1", 10)).containsExactly(execution.getId());
    }

    @Test
    void shouldFindExecutionIdsOfTheGivenTenantOnly() {
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant1);
        searchableOutputRepository.save(new SearchableOutput(tenant1, execution.getId(), "output", "value"));

        assertThat(searchableOutputRepository.findExecutionIds(tenant2, "output", Op.EQUALS, "value", 10)).isEmpty();
    }

    @Test
    void shouldLimitFoundExecutionIds() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        for (int i = 0; i < 3; i++) {
            searchableOutputRepository.save(new SearchableOutput(tenant, execution(tenant).getId(), "output", "value"));
        }

        assertThat(searchableOutputRepository.findExecutionIds(tenant, "output", Op.EQUALS, "value", 2)).hasSize(2);
    }

    @Test
    void shouldRejectUnsupportedOperationWhenFindingExecutionIds() {
        assertThatThrownBy(() -> searchableOutputRepository.findExecutionIds("tenant", "output", Op.REGEX, "value", 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFindOnlyExecutionsHavingTheOutputWithAnotherValueForNegativeOperations() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution matching = execution(tenant);
        Execution other = execution(tenant);
        Execution another = execution(tenant);
        Execution otherOutputId = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, matching.getId(), "orderIds", "ORD-1"));
        searchableOutputRepository.save(new SearchableOutput(tenant, other.getId(), "orderIds", "ORD-2"));
        searchableOutputRepository.save(new SearchableOutput(tenant, another.getId(), "orderIds", "ORD-3"));
        searchableOutputRepository.save(new SearchableOutput(tenant, otherOutputId.getId(), "other", "ORD-2"));

        assertThat(searchableOutputRepository.findExecutionIds(tenant, "orderIds", Op.NOT_EQUALS, "ORD-1", 10))
            .containsExactlyInAnyOrder(other.getId(), another.getId());
        assertThat(searchableOutputRepository.findExecutionIds(tenant, "orderIds", Op.NOT_IN, List.of("ORD-1", "ORD-2"), 10))
            .containsExactly(another.getId());
    }

    @Test
    void shouldFindExecutionIdsMatchingAnyOfTheValues() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution first = execution(tenant);
        Execution second = execution(tenant);
        Execution third = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, first.getId(), "orderIds", "ORD-1"));
        searchableOutputRepository.save(new SearchableOutput(tenant, second.getId(), "orderIds", "ORD-2"));
        searchableOutputRepository.save(new SearchableOutput(tenant, third.getId(), "orderIds", "ORD-3"));

        assertThat(searchableOutputRepository.findExecutionIds(tenant, "orderIds", Op.IN, List.of("ORD-1", "ORD-3"), 10))
            .containsExactlyInAnyOrder(first.getId(), third.getId());
    }

    @Test
    void shouldRejectScalarValueForListOperations() {
        assertThatThrownBy(() -> searchableOutputRepository.findExecutionIds("tenant", "output", Op.IN, "a", 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldListSearchableOutputsOfAnExecutionPagedAndOrderedByOutputId() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        Execution other = execution(tenant);
        for (String outputId : List.of("c", "a", "d", "b")) {
            searchableOutputRepository.save(new SearchableOutput(tenant, execution.getId(), outputId, "value-" + outputId));
        }
        searchableOutputRepository.save(new SearchableOutput(tenant, other.getId(), "other", "value"));

        var first = searchableOutputRepository.findByExecutionId(tenant, execution.getId(), Pageable.from(1, 3));
        var second = searchableOutputRepository.findByExecutionId(tenant, execution.getId(), Pageable.from(2, 3));

        assertThat(first.getTotal()).isEqualTo(4);
        assertThat(first).extracting(SearchableOutput::outputId).containsExactly("a", "b", "c");
        assertThat(first.getFirst()).isEqualTo(new SearchableOutput(tenant, execution.getId(), "a", "value-a"));
        assertThat(second).extracting(SearchableOutput::outputId).containsExactly("d");
    }

    @Test
    void shouldListNoSearchableOutputForAnotherTenantOrAnUnknownExecution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableOutputRepository.save(new SearchableOutput(tenant, execution.getId(), "output", "value"));

        var anotherTenant = searchableOutputRepository.findByExecutionId(TestsUtils.randomTenant(this.getClass().getSimpleName()), execution.getId(), Pageable.from(1, 10));
        var unknown = searchableOutputRepository.findByExecutionId(tenant, IdUtils.create(), Pageable.from(1, 10));

        assertThat(anotherTenant).isEmpty();
        assertThat(anotherTenant.getTotal()).isZero();
        assertThat(unknown).isEmpty();
    }
}
