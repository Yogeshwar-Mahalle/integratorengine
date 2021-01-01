package com.hsbc.gps.integrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntegratorEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntegratorEngineApplication.class, args);
	}
}
