-- Baseline schema matching voyage-app JPA entities (Hibernate ddl-auto=validate).
-- Spring AI vector_store table remains managed by spring.ai.vectorstore.pgvector.initialize-schema.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    role            VARCHAR(255) NOT NULL
);

CREATE TABLE hotels (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    city              VARCHAR(255) NOT NULL,
    price_per_night   DOUBLE PRECISION NOT NULL,
    description       VARCHAR(2000),
    amenities         VARCHAR(500),
    name_th           VARCHAR(255),
    city_th           VARCHAR(255),
    description_th    VARCHAR(2000),
    image_url         VARCHAR(500),
    gallery_urls      VARCHAR(1500),
    star_rating       INTEGER,
    guest_rating      DOUBLE PRECISION,
    review_count      INTEGER,
    address           VARCHAR(500),
    address_th        VARCHAR(500),
    neighborhood      VARCHAR(255),
    neighborhood_th   VARCHAR(255),
    check_in_from     VARCHAR(16),
    check_out_until   VARCHAR(16),
    phone             VARCHAR(64),
    manager_id        BIGINT REFERENCES users (id),
    saas_plan         VARCHAR(255) NOT NULL
);

CREATE INDEX idx_hotel_city ON hotels (city);
CREATE INDEX idx_hotel_manager ON hotels (manager_id);

CREATE TABLE room_inventory (
    id                BIGSERIAL PRIMARY KEY,
    hotel_id          BIGINT NOT NULL REFERENCES hotels (id),
    room_type         VARCHAR(255) NOT NULL,
    inventory_date    DATE NOT NULL,
    available_rooms   INTEGER NOT NULL,
    CONSTRAINT uk_room_inventory_hotel_date_type UNIQUE (hotel_id, inventory_date, room_type)
);

CREATE TABLE bookings (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id),
    hotel_id          BIGINT NOT NULL REFERENCES hotels (id),
    room_type         VARCHAR(255) NOT NULL,
    check_in_date     DATE NOT NULL,
    check_out_date    DATE NOT NULL,
    status            VARCHAR(255) NOT NULL,
    rate_plan         VARCHAR(255) NOT NULL,
    total_price       NUMERIC(12, 2) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_booking_hotel ON bookings (hotel_id);
CREATE INDEX idx_booking_hotel_checkin ON bookings (hotel_id, check_in_date);
CREATE INDEX idx_booking_user_status ON bookings (user_id, status);

CREATE TABLE payments (
    id                      BIGSERIAL PRIMARY KEY,
    booking_id              BIGINT NOT NULL UNIQUE REFERENCES bookings (id),
    amount                  NUMERIC(12, 2) NOT NULL,
    status                  VARCHAR(255) NOT NULL,
    provider                VARCHAR(255) NOT NULL,
    transaction_reference   VARCHAR(255) NOT NULL UNIQUE,
    processed_at            TIMESTAMPTZ NOT NULL
);

CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users (id),
    booking_id    BIGINT NOT NULL,
    type          VARCHAR(255) NOT NULL,
    message       VARCHAR(255) NOT NULL,
    is_read       BOOLEAN NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    token         VARCHAR(36) NOT NULL UNIQUE,
    user_id       BIGINT NOT NULL REFERENCES users (id),
    expiry_date   TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE job_runs (
    id                BIGSERIAL PRIMARY KEY,
    job_id            VARCHAR(64) NOT NULL,
    type              VARCHAR(64) NOT NULL,
    source            VARCHAR(32) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    payload_snippet   VARCHAR(512) NOT NULL,
    error             VARCHAR(1024),
    created_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ
);

CREATE INDEX idx_job_runs_created_at ON job_runs (created_at);

CREATE TABLE processed_hotel_events (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            VARCHAR(255) NOT NULL,
    schema_version      INTEGER NOT NULL,
    event_type          VARCHAR(255) NOT NULL,
    hotel_id            BIGINT NOT NULL,
    hotel_name          VARCHAR(255) NOT NULL,
    city                VARCHAR(255) NOT NULL,
    price_per_night     DOUBLE PRECISION NOT NULL,
    topic_name          VARCHAR(255) NOT NULL,
    message_key         VARCHAR(255) NOT NULL,
    partition_id        INTEGER NOT NULL,
    kafka_offset        BIGINT NOT NULL,
    consumer_group_id   VARCHAR(255) NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL,
    processed_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_processed_hotel_events_event_id UNIQUE (event_id)
);

CREATE TABLE dead_letter_hotel_events (
    id                    BIGSERIAL PRIMARY KEY,
    original_topic        VARCHAR(255) NOT NULL,
    dead_letter_topic     VARCHAR(255) NOT NULL,
    message_key           VARCHAR(255),
    partition_id          INTEGER NOT NULL,
    kafka_offset          BIGINT NOT NULL,
    payload               TEXT NOT NULL,
    original_event_id     VARCHAR(255),
    original_event_type   VARCHAR(255),
    original_hotel_id     BIGINT,
    error_class_name      VARCHAR(255),
    error_message         VARCHAR(2000),
    retry_status          VARCHAR(255) NOT NULL,
    retry_count           INTEGER NOT NULL,
    last_retried_at       TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    dead_lettered_at      TIMESTAMPTZ NOT NULL
);
