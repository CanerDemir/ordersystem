-- 1. Explicit Sequence Oluşturma (allocationSize / INCREMENT BY 50 ile Uyumlu)
CREATE SEQUENCE role_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE customer_role_seq START WITH 1 INCREMENT BY 50;

ALTER SEQUENCE role_seq OWNED BY roles.id;
ALTER SEQUENCE customer_role_seq OWNED BY customer_roles.id;

-- 2. roles Tablosu (DEFAULT NEXTVAL ile sequence bağlama)
CREATE TABLE roles (
                       id BIGINT PRIMARY KEY DEFAULT nextval('role_seq'),
                       name VARCHAR(50) NOT NULL CONSTRAINT uk_roles_name UNIQUE
);

-- 3. customer_roles Tablosu (Explicit Sequence ile)
CREATE TABLE customer_roles (
                                id BIGINT PRIMARY KEY DEFAULT nextval('customer_role_seq'),
                                customer_id BIGINT NOT NULL,
                                role_id BIGINT NOT NULL,
                                assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                CONSTRAINT uk_customer_roles_customer_role UNIQUE (customer_id, role_id),
                                CONSTRAINT fk_customer_roles_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
                                CONSTRAINT fk_customer_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

-- Indexing
CREATE INDEX idx_customer_roles_role_id ON customer_roles(role_id);

-- 4. Seed Roller (Sequence kullanılarak güvenli ekleme)
INSERT INTO roles (id, name) VALUES
                                 (nextval('role_seq'), 'ROLE_CUSTOMER'),
                                 (nextval('role_seq'), 'ROLE_OPERATION'),
                                 (nextval('role_seq'), 'ROLE_ADMIN');

-- 5. Data Migration: Existing Müşterilere ROLE_CUSTOMER Atanması
INSERT INTO customer_roles (id, customer_id, role_id)
SELECT
    nextval('customer_role_seq'),
    c.id,
    r.id
FROM customers c
         CROSS JOIN roles r
WHERE r.name = 'ROLE_CUSTOMER'
    ON CONFLICT (customer_id, role_id) DO NOTHING;