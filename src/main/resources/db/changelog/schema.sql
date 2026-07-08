-- Create customer table
CREATE TABLE customer (
                          id BIGSERIAL PRIMARY KEY,
                          name VARCHAR(255) NOT NULL
);

-- Create purchase_order table
CREATE TABLE purchase_order (
                         id BIGSERIAL PRIMARY KEY,
                         description VARCHAR(255) NOT NULL,
                         customer_id BIGINT NOT NULL,
                         CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

-- Create product table
CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    description VARCHAR(255) NOT NULL
);

-- order product many to many join table
CREATE TABLE order_product (
    order_id BIGINT NOT NULL REFERENCES purchase_order(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    PRIMARY KEY (order_id, product_id)
);

--index for trigram search
--This combination is highly effective for accelerating fuzzy searches, wildcard matches
--GIN indexes provide significantly faster search retrieval
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_customer_name_trgm ON customer USING gin (name gin_trgm_ops);

-- Index on FK column to accelerate JOIN between purchase_order and customer
CREATE INDEX idx_purchase_order_customer_id ON purchase_order (customer_id);
