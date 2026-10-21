CREATE TABLE IF NOT EXISTS searchable_outputs (
    `key`          VARCHAR(450) PRIMARY KEY,
    `tenant_id`    VARCHAR(150) NOT NULL,
    `execution_id` VARCHAR(150) NOT NULL,
    `output_id`    VARCHAR(150) NOT NULL,
    `value`        TEXT,
    INDEX searchable_outputs_output_id__value (`tenant_id`, `output_id`, `value`(255)),
    INDEX searchable_outputs_execution_id (`execution_id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
