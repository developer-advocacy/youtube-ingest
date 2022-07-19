package com.joshlong.youtube.runner;

import com.joshlong.youtube.YoutubeProperties;
import com.joshlong.youtube.client.Channel;
import com.joshlong.youtube.client.Video;
import com.joshlong.youtube.client.YoutubeClient;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
class IngestRunnerJob {

	@Bean
	ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory cf) {
		return new R2dbcTransactionManager(cf);
	}

	@Bean
	TransactionalOperator transactionalOperator(ReactiveTransactionManager rtm) {
		return TransactionalOperator.create(rtm);
	}

	@Bean
	YoutubeIngestJobRunner youtubeIngestJobRunner(YoutubeClient client, DatabaseClient databaseClient,
			YoutubeProperties properties) {
		return new YoutubeIngestJobRunner(client, databaseClient, properties.batch().channelUsername());
	}

}

@Slf4j
@RequiredArgsConstructor
class YoutubeIngestJobRunner implements ApplicationRunner {

	private final YoutubeClient client;

	private final DatabaseClient databaseClient;

	private final String channelUsername;

	private static void debugVideo(Video video) {
		if (log.isDebugEnabled()) {
			log.info("========================================================");
			log.info(video.toString());
		}
	}

	@Override
	public void run(ApplicationArguments args) {
		resetTablesFreshStatus()//
				.thenMany(writeChannel())//
				.flatMap(c -> client.getAllVideosByUsernameUploads(channelUsername))//
				.flatMap(this::doWriteVideo)//
				.subscribe(YoutubeIngestJobRunner::debugVideo);
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
				    channel_id
				)
				values (
				    :videoId,  :title,  :description, :publishedAt,
				    :standardThumbnail,  :categoryId,  :viewCount,
				    :favoriteCount, :commentCount, :likeCount , true, :channelId
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
				    like_count =  excluded.like_count
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
