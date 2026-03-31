create table member
(
    id         bigint auto_increment
          primary key,
    nickname   varchar(50)  not null comment '닉네임',
    status     varchar(20)  not null comment '회원 상태 (PENDING, ACTIVE)',
    created_at datetime(6)  not null comment '생성일시',
    updated_at datetime(6)  not null comment '수정일시'
);

create table social_account
(
    id               bigint auto_increment
        primary key,
    member_id        bigint       not null comment '회원 ID',
    provider         varchar(20)  not null comment '소셜 로그인 제공자',
    provider_user_id varchar(100) not null comment '소셜 로그인 제공자의 사용자 고유 ID',
    created_at       datetime(6)  not null comment '생성일시',
    updated_at       datetime(6)  not null comment '수정일시',
    constraint uk_provider_provider_user_id
        unique (provider, provider_user_id)
);

create index social_account_index_1
    on social_account (member_id);
