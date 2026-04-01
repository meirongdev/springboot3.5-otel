-- V1__Create_users_table.sql
CREATE TABLE "user" (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL
);

-- Insert sample data
INSERT INTO "user" (id, name, email) VALUES (1, 'Alice', 'alice@example.com');
INSERT INTO "user" (id, name, email) VALUES (2, 'Bob', 'bob@example.com');
INSERT INTO "user" (id, name, email) VALUES (3, 'Charlie', 'charlie@example.com');
