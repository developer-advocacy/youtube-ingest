package com.joshlong.youtubeingest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class DefaultYoutubeClientTest {

    private final DefaultYoutubeClient youtubeClient;

    DefaultYoutubeClientTest(@Autowired DefaultYoutubeClient youtubeClient) {
        this.youtubeClient = youtubeClient;
    }

    @Test
    void channel() throws Exception {

        var springSourceDevChannelId = "UC7yfnfvEUlXUIfm8rGLwZdA";
        var springSourceDevUsername = "springsourcedev";
        var channel = this.youtubeClient.getChannelByUsername(springSourceDevUsername);
        Assertions.assertTrue(channel.title().contains("Spring"));
        log.info(channel.toString());

    }
}