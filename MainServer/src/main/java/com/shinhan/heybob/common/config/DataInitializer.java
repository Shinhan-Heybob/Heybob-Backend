package com.shinhan.heybob.common.config;

import com.shinhan.heybob.domain.user.entity.User;
import com.shinhan.heybob.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local") // local 프로파일에서만 실행
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository) {
        return args -> {
            // 테스트용 사용자 데이터가 없으면 생성
            if (userRepository.count() == 0) {
                log.info("📊 테스트용 더미 사용자 데이터 생성 시작...");

                // User 1 - 밥약 생성자 (김민수)
                User creator = User.builder()
                        .studentId("20210101")
                        .password("password123")
                        .name("김민수")
                        .university("신한대학교")
                        .department("컴퓨터공학과")
                        .profileUrl("https://example.com/minsu.jpg")
                        .build();
                userRepository.save(creator);
                log.info("✅ 밥약 생성자 생성: ID={}, 이름={}, 학번={}", creator.getId(), creator.getName(), creator.getStudentId());

                // User 2 - 참여자 1 (이지은)
                User participant1 = User.builder()
                        .studentId("20210202")
                        .password("password123")
                        .name("이지은")
                        .university("신한대학교")
                        .department("경영학과")
                        .profileUrl("https://example.com/jieun.jpg")
                        .build();
                userRepository.save(participant1);
                log.info("✅ 참여자 1 생성: ID={}, 이름={}, 학번={}", participant1.getId(), participant1.getName(), participant1.getStudentId());

                // User 3 - 참여자 2 (박준호)
                User participant2 = User.builder()
                        .studentId("20210303")
                        .password("password123")
                        .name("박준호")
                        .university("신한대학교")
                        .department("전자공학과")
                        .profileUrl("https://example.com/junho.jpg")
                        .build();
                userRepository.save(participant2);
                log.info("✅ 참여자 2 생성: ID={}, 이름={}, 학번={}", participant2.getId(), participant2.getName(), participant2.getStudentId());

                // User 4 - 채팅 테스트용 1 (최수연)
                User chatUser1 = User.builder()
                        .studentId("20210404")
                        .password("password123")
                        .name("최수연")
                        .university("신한대학교")
                        .department("디자인학과")
                        .profileUrl("https://example.com/suyeon.jpg")
                        .build();
                userRepository.save(chatUser1);
                log.info("✅ 채팅 사용자 1 생성: ID={}, 이름={}, 학번={}", chatUser1.getId(), chatUser1.getName(), chatUser1.getStudentId());

                // User 5 - 채팅 테스트용 2 (정우진)
                User chatUser2 = User.builder()
                        .studentId("20210505")
                        .password("password123")
                        .name("정우진")
                        .university("신한대학교")
                        .department("경제학과")
                        .profileUrl("https://example.com/woojin.jpg")
                        .build();
                userRepository.save(chatUser2);
                log.info("✅ 채팅 사용자 2 생성: ID={}, 이름={}, 학번={}", chatUser2.getId(), chatUser2.getName(), chatUser2.getStudentId());

                log.info("📊 테스트용 더미 데이터 생성 완료! 총 {} 명의 사용자", userRepository.count());
            } else {
                log.info("📊 기존 사용자 데이터 존재: {} 명", userRepository.count());
            }
        };
    }
}