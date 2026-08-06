package com.voyage.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class VoyageAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(VoyageAppApplication.class, args);
	}
}
