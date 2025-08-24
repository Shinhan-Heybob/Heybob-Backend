package com.shinhan.heybob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class HeybobApplication {

	public static void main(String[] args) {
		SpringApplication.run(HeybobApplication.class, args);
	}

}
