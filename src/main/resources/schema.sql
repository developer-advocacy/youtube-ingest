create table if not exists yt_channels
(
    channel_id   varchar(255) not null primary key,
    description  varchar(255) not null,
    published_at timestamp    not null,
    title        varchar(255) not null
);