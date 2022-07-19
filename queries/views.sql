
create view spring_tips_videos as select *  from yt_videos v where v.video_id in (
    select pv.video_id from yt_playlist_videos pv where pv.playlist_id = (
        select p.playlist_id from yt_playlists p where p.title ilike '%spring%tips%'
    )
);



