package io.kestra.core.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Type;
import io.kestra.core.repositories.ExecutionOutputRepositoryInterface;
import io.kestra.core.searchable.SearchableOutput;
import io.kestra.core.searchable.SearchableOutputRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class ExecutionOutputServiceTest {
    @Inject
    private ExecutionOutputService executionOutputService;

    @Inject
    private ExecutionOutputRepositoryInterface executionOutputRepository;

    @Inject
    private SearchableOutputRepositoryInterface searchableOutputRepository;

    private Flow flow(Output... outputs) {
        return Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.test")
            .outputs(List.of(outputs))
            .build();
    }

    private Output output(String id, Type type, Boolean searchable) {
        return Output.builder().id(id).type(type).searchable(searchable).value("{{ outputs.x }}").build();
    }

    private Execution execution(String tenant, State.Type state) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State(state))
            .build();
    }

    @Test
    void shouldSaveAndGetOutputs() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);
        Map<String, Object> outputs = Map.of(
            "key1", "value1",
            "key2", 42,
            "key3", Map.of("nested", "value"),
            "key4", List.of(1, 2, 3)
        );

        // When
        executionOutputService.saveOutputs(flow(), execution, outputs);

        // Then
        Map<String, Object> retrieved = executionOutputService.getOutputs(execution);
        assertThat(retrieved).hasSize(4);
        assertThat(retrieved.get("key1")).isEqualTo("value1");
        assertThat(retrieved.get("key2")).isEqualTo(42);
        assertThat(retrieved.get("key3")).isInstanceOf(Map.class);
        assertThat((List<?>) retrieved.get("key4")).hasSize(3);

        var saved = executionOutputRepository.findById(tenant, execution.getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().value()).isNotNull();
        assertThat(saved.get().uri()).isNull();
    }

    @Test
    void shouldNotSaveWhenOutputsAreEmpty() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);

        // When
        executionOutputService.saveOutputs(flow(), execution, Collections.emptyMap());

        // Then
        assertThat(executionOutputRepository.findById(tenant, execution.getId())).isEmpty();
        assertThat(executionOutputService.getOutputs(execution)).isNull();
    }

    @Test
    void shouldReturnNullWhenNotFound() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Map<String, Object> outputs = executionOutputService.getOutputs(execution(tenant, State.Type.SUCCESS));

        // Then
        assertThat(outputs).isNull();
    }

    @Test
    void shouldReturnNullWhenExecutionIsNotTerminated() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);
        executionOutputService.saveOutputs(flow(), execution, Map.of("key", "value"));

        // When
        Map<String, Object> outputs = executionOutputService.getOutputs(execution.withState(State.Type.RUNNING));

        // Then
        assertThat(outputs).isNull();
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldReturnDeprecatedOutputsFieldWhenSet() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Map<String, Object> legacyOutputs = Map.of("legacyKey", "legacyValue", "count", 42);
        Execution execution = execution(tenant, State.Type.SUCCESS).withOutputs(legacyOutputs);

        // When - nothing saved to the repository, the pre-2.0 field must be used
        Map<String, Object> outputs = executionOutputService.getOutputs(execution);

        // Then
        assertThat(outputs).containsExactlyInAnyOrderEntriesOf(legacyOutputs);
    }

    @Test
    void shouldPurgeOnlyTheGivenExecutions() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution1 = execution(tenant, State.Type.SUCCESS);
        Execution execution2 = execution(tenant, State.Type.SUCCESS);
        executionOutputService.saveOutputs(flow(), execution1, Map.of("key", "value1"));
        executionOutputService.saveOutputs(flow(), execution2, Map.of("key", "value2"));

        // When
        int purged = executionOutputService.purge(List.of(execution1));

        // Then
        assertThat(purged).isEqualTo(1);
        assertThat(executionOutputRepository.findById(tenant, execution1.getId())).isEmpty();
        assertThat(executionOutputRepository.findById(tenant, execution2.getId())).isPresent();
    }

    @Test
    void shouldSaveOnlySearchableOutputs() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);
        Flow flow = flow(
            output("name", Type.STRING, true),
            output("count", Type.INT, true),
            output("ignored", Type.STRING, false),
            output("unset", Type.STRING, null)
        );

        // When
        executionOutputService.saveOutputs(flow, execution, Map.of("name", "value", "count", 42, "ignored", "a", "unset", "b"));

        // Then
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "name")).contains(new SearchableOutput(tenant, execution.getId(), "name", "value"));
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "count")).contains(new SearchableOutput(tenant, execution.getId(), "count", "42"));
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "ignored")).isEmpty();
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "unset")).isEmpty();
    }

    @Test
    void shouldNotSaveSearchableOutputsWhenNoneIsSearchable() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);

        // When
        executionOutputService.saveOutputs(flow(output("name", Type.STRING, false)), execution, Map.of("name", "value"));

        // Then
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "name")).isEmpty();
    }

    @Test
    void shouldNotSaveSearchableOutputsWhenOutputsAreEmpty() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);

        // When
        executionOutputService.saveOutputs(flow(output("name", Type.STRING, true)), execution, Collections.emptyMap());

        // Then
        assertThat(searchableOutputRepository.findById(tenant, execution.getId(), "name")).isEmpty();
    }

    @Test
    void shouldFailWhenSearchableOutputTypeIsNotSupported() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);

        // When / Then
        assertThatThrownBy(() -> executionOutputService.saveOutputs(flow(output("json", Type.JSON, true)), execution, Map.of("json", Map.of("a", "b"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSaveOutputsWhenTheFlowDeclaresNone() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);
        Flow flowWithoutOutputs = Flow.builder().id("test-flow").namespace("io.kestra.test").build();

        // When
        executionOutputService.saveOutputs(flowWithoutOutputs, execution, Map.of("key", "value"));

        // Then
        assertThat(executionOutputService.getOutputs(execution)).containsEntry("key", "value");
    }
}
