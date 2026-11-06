ALTER TABLE app_user ADD COLUMN credentials_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();
