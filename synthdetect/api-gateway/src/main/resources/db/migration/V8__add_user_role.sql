CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

ALTER TABLE users ADD COLUMN role user_role NOT NULL DEFAULT 'USER';

CREATE INDEX idx_users_role ON users(role) WHERE role = 'ADMIN';
