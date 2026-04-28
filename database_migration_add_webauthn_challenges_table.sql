CREATE TABLE IF NOT EXISTS webauthn_challenges (
    request_id UUID PRIMARY KEY,
    email VARCHAR(255),
    user_id BIGINT,
    challenge VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    request_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_webauthn_challenge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_webauthn_challenges_expires_at ON webauthn_challenges(expires_at);