CREATE USER local WITH PASSWORD 'local' CREATEDB;
CREATE DATABASE decision_engine_db
    WITH
    OWNER = local
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

GRANT ALL PRIVILEGES ON DATABASE decision_engine_db TO local;

\c decision_engine_db;

CREATE TABLE dmn_models (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(255) NOT NULL,
                            version VARCHAR(50) NOT NULL,
                            content TEXT NOT NULL,
                            status VARCHAR(20) DEFAULT 'ACTIVE',
                            created_at TIMESTAMP DEFAULT NOW(),
                            created_by VARCHAR(100),
                            CONSTRAINT unique_name_version UNIQUE(name, version)
);

CREATE INDEX idx_active_models ON dmn_models(name, status) WHERE status = 'ACTIVE';

GRANT ALL PRIVILEGES ON TABLE dmn_models TO local;
GRANT USAGE, SELECT ON SEQUENCE dmn_models_id_seq TO local;