package com.joshlong.youtube.runner;

import com.joshlong.youtube.client.Channel;
import com.joshlong.youtube.client.Playlist;
import com.joshlong.youtube.client.Video;
import com.joshlong.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
class YoutubeIngestJobRunner implements ApplicationRunner {

	private final YoutubeClient client;

	private final DatabaseClient databaseClient;

	private final String channelUsername;

	@Override
	public void run(ApplicationArguments args) {
		resetTablesFreshStatus()//
				.thenMany(writeChannel())//
				.flatMap(channel -> client.getAllPlaylistsByChannel(channel.channelId()).flatMap(this::doWritePlaylist))// playlists
				// .flatMap( playlist ->
				// client.getAllVideosByPlaylist(playlist.playlistId()) ) // videos for
				// playlists todo
				.thenMany(c -> client.getAllVideosByUsernameUploads(channelUsername).flatMap(this::doWriteVideo))// videos
				.doFinally(st -> log.info("finished...")).subscribe();
	}

	private Mono<Playlist> doWritePlaylist(Playlist playlist) {
		var sql = """
				insert into yt_playlists (
				    playlist_id,
				    channel_id,
				    published_at,
				    title,
				    description,
				    item_count,
				    fresh
				)
				values( :playlistId  ,  :channelId,  :publishedAt, :title, :description, :itemCount , true )
				on conflict on constraint yt_playlists_pkey
				do update SET fresh = true where yt_playlists.playlist_id = :playlistId
				""";

		return this.databaseClient.sql(sql)//
				.bind("itemCount", playlist.itemCount()).bind("description", playlist.description())
				.bind("title", playlist.title()).bind("publishedAt", playlist.publishedAt())
				.bind("channelId", playlist.channelId()).bind("playlistId", playlist.playlistId()).fetch().rowsUpdated()
				.map(count -> playlist);//
	}

	private Mono<Video> doWriteVideo(Video video) {
		var videoSql = """
				insert into yt_videos (
				    video_id ,
				    title,
				    description,
				    published_at ,
				    standard_thumbnail,
				    category_id,
				    view_count,
				    favorite_count,
				    comment_count  ,
				    like_count ,
				    fresh,
				    channel_id,
				    tags
				)
				values (
				    :videoId,  :title,  :description, :publishedAt,
				    :standardThumbnail,  :categoryId,  :viewCount,
				    :favoriteCount, :commentCount, :likeCount , true, :channelId, :tags
				)
				on conflict on CONSTRAINT yt_videos_pkey
				do update set
				    fresh = true,
				    video_id  = excluded.video_id,
				    title = excluded.title,
				    description = excluded.description,
				    published_at  = excluded.published_at,
				    standard_thumbnail = excluded.standard_thumbnail,
				    category_id = excluded.category_id,
				    view_count = excluded.view_count,
				    favorite_count = excluded.favorite_count,
				    comment_count   = excluded.comment_count,
				    like_count =  excluded.like_count ,
				    tags = excluded.tags
				 where
				    yt_videos.video_id =  :videoId
				""";

		return databaseClient//
				.sql(videoSql)//
				.bind("videoId", video.videoId())//
				.bind("title", video.title()) //
				.bind("description", video.description())//
				.bind("publishedAt", video.publishedAt())//
				.bind("standardThumbnail", video.standardThumbnail().toExternalForm())//
				.bind("categoryId", video.categoryId())//
				.bind("viewCount", video.viewCount())//
				.bind("favoriteCount", video.favoriteCount())//
				.bind("commentCount", video.commentCount())//
				.bind("tags", video.tags().toArray(new String[0]))//
				.bind("likeCount", video.likeCount())//
				.bind("channelId", video.channelId())//
				.fetch()//
				.rowsUpdated()//
				.map(count -> video);
	}

	private Mono<Channel> writeChannel() {
		var sql = """
				    insert into yt_channels(channel_id, description, published_at, title, fresh)
				    values (  :channelId , :description, :publishedAt, :title, true)
				    on conflict on constraint yt_channels_pkey
				    do update SET fresh = true where yt_channels.channel_id = :channelId
				""";
		return client//
				.getChannelByUsername(this.channelUsername)//
				.flatMap(c -> databaseClient.sql(sql)//
						.bind("channelId", c.channelId())//
						.bind("description", c.description())//
						.bind("publishedAt", c.publishedAt())//
						.bind("title", c.title())//
						.fetch()//
						.rowsUpdated()//
						.thenReturn(c)//
				);
	}

	private Flux<Integer> resetTablesFreshStatus() {
		return Flux//
				.fromArray("yt_playlist_videos,yt_channels,yt_playlists,yt_videos".split(","))//
				.flatMap(table -> databaseClient//
						.sql("update " + table + " set fresh = false")//
						.fetch()//
						.rowsUpdated()//
				);
	}

}
