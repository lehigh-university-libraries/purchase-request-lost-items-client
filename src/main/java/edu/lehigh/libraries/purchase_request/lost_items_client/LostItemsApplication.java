package edu.lehigh.libraries.purchase_request.lost_items_client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

import lombok.extern.slf4j.Slf4j;

@ComponentScan(basePackages = "edu.lehigh.libraries.purchase_request")
@SpringBootApplication
@Slf4j
public class LostItemsApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(LostItemsApplication.class);
	}

	public static void main(String[] args) {
		log.info("Starting the Lost Items Application");
		SpringApplication.run(LostItemsApplication.class, args);
	}

}
