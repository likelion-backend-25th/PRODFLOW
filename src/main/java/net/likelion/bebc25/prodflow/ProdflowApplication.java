package net.likelion.bebc25.prodflow;

import net.likelion.bebc25.prodflow.payment.config.PortOneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // @Scheduled 주기 작업 허용
@EnableConfigurationProperties(PortOneProperties.class)
public class ProdflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProdflowApplication.class, args);
    }

}
