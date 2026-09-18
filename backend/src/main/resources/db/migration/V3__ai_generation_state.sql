ALTER TABLE trials ADD COLUMN generation_status VARCHAR(20) NOT NULL DEFAULT 'IDLE';
ALTER TABLE trials ADD COLUMN generation_stage VARCHAR(20);
ALTER TABLE trials ADD COLUMN generation_turn INTEGER;
ALTER TABLE trials ADD COLUMN generation_request_id VARCHAR(36);
ALTER TABLE trials ADD COLUMN generation_started_at TIMESTAMPTZ;
ALTER TABLE verdicts ALTER COLUMN winner_side DROP NOT NULL;
