package br.com.desafio.portabilidade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PortabilidadeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortabilidadeApplication.class, args);
    }
}
