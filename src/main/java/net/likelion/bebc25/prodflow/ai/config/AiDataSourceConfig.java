package net.likelion.bebc25.prodflow.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiDataSourceConfig {

    // AI 조회 전용 DataSource 생성
    // 기존 JPA에서 사용하는 기본 DataSource와 분리하여 관리
    @Bean(name = "aiDataSource", defaultCandidate = false)
    @ConfigurationProperties(prefix = "ai.datasource")
    public HikariDataSource aiDataSource() {

        // application.yml의 ai.datasource 설정을 주입받는 연결 풀
        // SELECT 권한만 가진 prodflow_ai 계정으로 MySQL에 연결
        return new HikariDataSource();
    }
}