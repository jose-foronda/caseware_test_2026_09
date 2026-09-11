-- Legacy table (preserved for backward compatibility with existing Long-id entity)
CREATE TABLE IF NOT EXISTS dmn_models (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100),
    CONSTRAINT uk_dmn_models_name_version UNIQUE (name, version)
);

-- Model owners (lenders / organizations that own DMN models)
CREATE TABLE IF NOT EXISTS model_owner (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT chk_model_owner_name_trimmed CHECK (name = TRIM(name))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_model_owner_name ON model_owner (LOWER(TRIM(name)));

-- DMN model type catalog (e.g. Rates, Rules, Guidelines)
CREATE TABLE IF NOT EXISTS dmn_model_type (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT chk_dmn_model_type_name_trimmed CHECK (name = TRIM(name))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_model_type_name ON dmn_model_type (LOWER(TRIM(name)));

-- Core DMN model definition
CREATE TABLE IF NOT EXISTS dmn_model (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(255)  NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    description VARCHAR(500),
    model_owner_id UUID NOT NULL,
    dmn_model_type_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT fk_dmn_model_owner FOREIGN KEY (model_owner_id) REFERENCES model_owner (id),
    CONSTRAINT fk_dmn_model_type FOREIGN KEY (dmn_model_type_id) REFERENCES dmn_model_type (id),
    CONSTRAINT chk_dmn_model_name_trimmed CHECK (name = TRIM(name))
);
-- Only one ACTIVE model per owner+type combination
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_model_owner_type_active
    ON dmn_model (model_owner_id, dmn_model_type_id)
    WHERE status = 'ACTIVE';
-- Only one model per owner+type+version combination
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_model_owner_type_version
    ON dmn_model (model_owner_id, dmn_model_type_id, LOWER(TRIM(version)));

-- SCESIM simulation files linked to a DMN model
CREATE TABLE IF NOT EXISTS dmn_model_simulation (
    id UUID PRIMARY KEY,
    dmn_model_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT chk_dmn_simulation_name_trimmed CHECK (name = TRIM(name)),
    CONSTRAINT fk_dmn_simulation_model FOREIGN KEY (dmn_model_id) REFERENCES dmn_model (id)
);
-- Only one simulation per model + version combination
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_simulation_name_version
    ON dmn_model_simulation (dmn_model_id, LOWER(TRIM(version)));
-- Only one ACTIVE simulation per DMN model
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_simulation_dmn_model_id_active
    ON dmn_model_simulation (dmn_model_id)
    WHERE status = 'ACTIVE';

-- Data type catalog
CREATE TABLE IF NOT EXISTS dmn_data_type (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_data_type_name ON dmn_data_type (LOWER(TRIM(name)));


-- Versioned content for each data type
CREATE TABLE IF NOT EXISTS dmn_data_type_version (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    dmn_data_type_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT fk_data_type_version_type FOREIGN KEY (dmn_data_type_id) REFERENCES dmn_data_type (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_data_type_version_dmn_data_type_id_version
    ON dmn_data_type_version (dmn_data_type_id, LOWER(TRIM(version)));


-- Junction: links a DMN model to specific data type versions
CREATE TABLE IF NOT EXISTS dmn_model_data_types (
    id UUID PRIMARY KEY,
    dmn_model_id UUID NOT NULL,
    dmn_data_type_id BIGINT NOT NULL,
    dmn_data_type_version_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT fk_model_data_types_model FOREIGN KEY (dmn_model_id) REFERENCES dmn_model (id),
    CONSTRAINT fk_model_data_types_type FOREIGN KEY (dmn_data_type_id) REFERENCES dmn_data_type (id),
    CONSTRAINT fk_model_data_types_version FOREIGN KEY (dmn_data_type_version_id) REFERENCES dmn_data_type_version (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmn_model_data_types_dmn_model_id_dmn_data_type_id ON dmn_model_data_types (dmn_model_id, dmn_data_type_id);

