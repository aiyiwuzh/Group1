CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    usc_id CHAR(10) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE `groups` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL,

    CONSTRAINT fk_group_owner
        FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE memberships (
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    PRIMARY KEY (group_id, user_id),

    CONSTRAINT fk_membership_group
        FOREIGN KEY (group_id) REFERENCES `groups`(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_membership_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);