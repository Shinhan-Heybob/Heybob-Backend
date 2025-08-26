package com.shinhan.heybob;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
	org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class
})
@ActiveProfiles("test")
class HeybobApplicationTests {

	@Test
	void contextLoads() {
	}
}
