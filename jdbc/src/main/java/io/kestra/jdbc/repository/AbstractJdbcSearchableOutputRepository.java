package io.kestra.jdbc.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.searchable.SearchableOutput;
import io.kestra.core.searchable.SearchableOutputRepositoryInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.AbstractJdbcRepository;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

/**
 * The {@code searchable_outputs} table stores one plain column per attribute so that the value can be indexed,
 * and has no {@code deleted} column, so the inherited default filters are neutralized.
 */
public abstract class AbstractJdbcSearchableOutputRepository extends AbstractJdbcCrudRepository<SearchableOutput> implements SearchableOutputRepositoryInterface {
    public static final Field<String> EXECUTION_ID_FIELD = field("execution_id", String.class);
    public static final Field<String> OUTPUT_ID_FIELD = field("output_id", String.class);
    public static final Field<String> VALUE_FIELD = field("value", String.class);

    public AbstractJdbcSearchableOutputRepository(AbstractJdbcRepository<SearchableOutput> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public SearchableOutput save(SearchableOutput searchableOutput) {
        Map<Field<Object>, Object> fields = HashMap.newHashMap(4);
        fields.put(field("tenant_id"), searchableOutput.tenantId());
        fields.put(field("execution_id"), searchableOutput.executionId());
        fields.put(field("output_id"), searchableOutput.outputId());
        fields.put(io.kestra.jdbc.repository.AbstractJdbcRepository.VALUE_FIELD, searchableOutput.value());
        jdbcRepository.persist(searchableOutput, fields);
        return searchableOutput;
    }

    @Override
    public Optional<SearchableOutput> findById(String tenantId, String executionId, String outputId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select()
                .from(this.jdbcRepository.getTable())
                .where(buildTenantCondition(tenantId))
                .and(EXECUTION_ID_FIELD.eq(executionId))
                .and(OUTPUT_ID_FIELD.eq(outputId))
                .limit(1)
                .fetchOptional()
                .map(AbstractJdbcSearchableOutputRepository::toSearchableOutput));
    }

    @Override
    public ArrayListTotal<SearchableOutput> findByExecutionId(String tenantId, String executionId, Pageable pageable) {
        Pageable ordered = Pageable.from(pageable.getNumber(), pageable.getSize(), Sort.of(Sort.Order.asc("output_id")));
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select()
                    .from(this.jdbcRepository.getTable())
                    .where(buildTenantCondition(tenantId))
                    .and(EXECUTION_ID_FIELD.eq(executionId));

                return this.jdbcRepository.fetchPage(DSL.using(configuration), select, ordered, AbstractJdbcSearchableOutputRepository::toSearchableOutput);
            });
    }

    @Override
    public List<String> findExecutionIds(String tenantId, String outputId, QueryFilter.Op operation, Object value, int limit) {
        if (value == null) {
            throw new IllegalArgumentException("Operation %s requires a non-null value on searchable outputs.".formatted(operation));
        }

        Condition valueCondition = switch (operation) {
            case EQUALS -> VALUE_FIELD.eq(value.toString());
            case NOT_EQUALS -> VALUE_FIELD.ne(value.toString());
            case IN -> VALUE_FIELD.in(ListUtils.convertToListString(value));
            case NOT_IN -> VALUE_FIELD.notIn(ListUtils.convertToListString(value));
            default -> throw new IllegalArgumentException("Operation %s is not supported on searchable outputs.".formatted(operation));
        };

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(EXECUTION_ID_FIELD)
                .from(this.jdbcRepository.getTable())
                .where(buildTenantCondition(tenantId))
                .and(OUTPUT_ID_FIELD.eq(outputId))
                .and(valueCondition)
                .limit(limit)
                .fetch(EXECUTION_ID_FIELD));
    }

    @Override
    public int purge(List<Execution> executions) {
        return purge(DSL.noCondition(), EXECUTION_ID_FIELD.in(executions.stream().map(Execution::getId).toList()));
    }

    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    @Override
    protected Condition defaultFilter() {
        return DSL.noCondition();
    }

    private static SearchableOutput toSearchableOutput(Record record) {
        return new SearchableOutput(record.get(TENANT_ID_FIELD), record.get(EXECUTION_ID_FIELD), record.get(OUTPUT_ID_FIELD), record.get(VALUE_FIELD));
    }
}
