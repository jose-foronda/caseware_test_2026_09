-- Evaluation sample inputs for DMN models
CREATE TABLE IF NOT EXISTS dmn_model_evaluation_sample (
    id UUID PRIMARY KEY,
    dmn_model_id UUID NOT NULL,
    description VARCHAR(500),
    input JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT fk_evaluation_sample_model FOREIGN KEY (dmn_model_id) REFERENCES dmn_model (id)
);
