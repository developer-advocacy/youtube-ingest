create table if not exists yt_channels
(
    channel_id   varchar(255) not null primary key,
    description  varchar(255) not null,
    published_at timestamp    not null,
    title        varchar(255) not null,
    fresh        boolean default false
);

create table if not exists yt_playlists
(
    playlist_id  varchar(255) not null primary key,
    channel_id   varchar(255) not null,
    published_at timestamp    not null,
    title        varchar(255) not null,
    description  text         not null,
    item_count   int          not null default 0,
    fresh        boolean               default false
);