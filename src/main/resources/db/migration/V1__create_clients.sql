CREATE TABLE clients (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    unique_code        VARCHAR(36)     NOT NULL,
    deleted            BOOLEAN         NOT NULL DEFAULT FALSE,
    created_date       DATETIME        NOT NULL,
    last_modified_date DATETIME        NOT NULL,
    name               VARCHAR(150)    NOT NULL,
    email              VARCHAR(255)    NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_clients_unique_code UNIQUE (unique_code),
    CONSTRAINT uq_clients_email       UNIQUE (email)
);

CREATE INDEX idx_clients_deleted ON clients (deleted);
