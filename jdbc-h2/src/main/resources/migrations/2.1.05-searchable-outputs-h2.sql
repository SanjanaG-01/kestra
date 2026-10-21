CREATE TABLE IF NOT EXISTS searchable_outputs (
    "key"          VARCHAR(450) PRIMARY KEY,
    "tenant_id"    VARCHAR(150) NOT NULL,
    "execution_id" VARCHAR(150) NOT NULL,
    "output_id"    VARCHAR(150) NOT NULL,
    "value"        VARCHAR(65536)
);

CREATE INDEX IF NOT EXISTS searchable_outputs_output_id__value ON searchable_outputs ("tenant_id", "output_id", "value");
CREATE INDEX IF NOT EXISTS searchable_outputs_execution_id ON searchable_outputs ("execution_id");
