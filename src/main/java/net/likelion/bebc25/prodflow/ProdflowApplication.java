package net.likelion.bebc25.prodflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ProdflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProdflowApplication.class, args);
    }

}
