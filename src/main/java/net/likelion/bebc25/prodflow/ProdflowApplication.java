package net.likelion.bebc25.prodflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling // @Scheduled 주기 작업 허용
public class ProdflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProdflowApplication.class, args);
    }

}
