-- ============================================================
-- Ramses Bazaar initial schema
-- Flyway: V1__init.sql
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------
-- Catalogue
--
-- The admin panel saves the whole product list at once and
-- sends the revision it started from. `catalogue.rev` is that
-- revision: a save against an older one is rejected, so two
-- devices editing at the same time cannot overwrite each other.
-- ------------------------------------------------------------

CREATE TABLE catalogue (
    id          smallint    NOT NULL,
    rev         integer     NOT NULL DEFAULT 0,
    updated_at  timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (id),
    CHECK (id = 1)
);

INSERT INTO catalogue (id, rev) VALUES (1, 0);

CREATE TABLE product (
    id           text          NOT NULL,
    position     integer       NOT NULL,
    name         text          NOT NULL,
    category     text          NOT NULL,
    price        numeric(10,2) NOT NULL,
    tag          text          NOT NULL DEFAULT '',
    description  text          NOT NULL DEFAULT '',
    in_stock     boolean       NOT NULL DEFAULT true,
    image        text,
    created_at   bigint        NOT NULL,

    PRIMARY KEY (id),
    CHECK (category IN ('jewelry', 'clothes', 'home', 'incense', 'collectibles')),
    CHECK (tag IN ('', 'new', 'best', 'sale')),
    CHECK (price >= 0)
);

-- Product photos, addressed by a hash of their bytes.
CREATE TABLE product_image (
    image_key     text        NOT NULL,
    content_type  text        NOT NULL,
    data          bytea       NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (image_key)
);

-- ------------------------------------------------------------
-- Shop settings (single row) and the admin password
-- ------------------------------------------------------------

CREATE TABLE shop_settings (
    id                smallint NOT NULL,
    whatsapp          text,
    phone             text,
    address           text,
    iban              text,
    bank              text,
    account_name      text,
    email             text,
    password_hash     text,
    password_version  integer  NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CHECK (id = 1)
);

INSERT INTO shop_settings (id, whatsapp, phone, address)
VALUES (1, '302721096103', '27210 96103', 'Στοά Λόντου 1, Καλαμάτα 24100');

-- ------------------------------------------------------------
-- Orders
-- ------------------------------------------------------------

CREATE TABLE shop_order (
    id                uuid          NOT NULL DEFAULT gen_random_uuid(),
    customer_name     text          NOT NULL,
    customer_phone    text          NOT NULL,
    customer_email    text          NOT NULL,
    customer_address  text          NOT NULL,
    notes             text          NOT NULL DEFAULT '',
    subtotal          numeric(10,2) NOT NULL,
    shipping          numeric(10,2) NOT NULL,
    total             numeric(10,2) NOT NULL,
    status            text          NOT NULL DEFAULT 'NEW',
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    PRIMARY KEY (id),
    CHECK (status IN ('NEW', 'CONFIRMED', 'SHIPPED', 'CANCELLED'))
);

CREATE INDEX shop_order_created_at_idx ON shop_order (created_at DESC);

CREATE TABLE order_item (
    id            uuid          NOT NULL DEFAULT gen_random_uuid(),
    order_id      uuid          NOT NULL REFERENCES shop_order (id) ON DELETE CASCADE,
    position      integer       NOT NULL,
    product_id    text          NOT NULL,
    product_name  text          NOT NULL,
    unit_price    numeric(10,2) NOT NULL,
    quantity      integer       NOT NULL,

    PRIMARY KEY (id),
    CHECK (quantity > 0)
);

CREATE INDEX order_item_order_id_idx ON order_item (order_id);
