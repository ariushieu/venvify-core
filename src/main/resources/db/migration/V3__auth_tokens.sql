-- V3: bảng token cho auth (refresh stateful + xác thực email). Không đụng V1/V2.

create table refresh_tokens (
    id          bigint       not null auto_increment,
    public_id   varchar(36)  not null,
    user_id     bigint       not null,
    token_hash  varchar(64)  not null,
    expires_at  datetime(6)  not null,
    revoked_at  datetime(6),
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    version     bigint       not null,
    primary key (id),
    constraint uq_refresh_token_public_id unique (public_id),
    constraint uq_refresh_token_hash unique (token_hash),
    constraint fk_refresh_token_user foreign key (user_id) references users (id),
    index idx_refresh_token_user (user_id)
) engine=InnoDB;

create table email_verification_tokens (
    id          bigint       not null auto_increment,
    public_id   varchar(36)  not null,
    user_id     bigint       not null,
    token_hash  varchar(64)  not null,
    expires_at  datetime(6)  not null,
    used_at     datetime(6),
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  not null,
    version     bigint       not null,
    primary key (id),
    constraint uq_evt_public_id unique (public_id),
    constraint uq_evt_hash unique (token_hash),
    constraint fk_evt_user foreign key (user_id) references users (id),
    index idx_evt_user (user_id)
) engine=InnoDB;
