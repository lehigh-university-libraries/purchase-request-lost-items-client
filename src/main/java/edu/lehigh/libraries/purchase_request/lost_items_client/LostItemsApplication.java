package edu.lehigh.libraries.purchase_request.lost_items_client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.context.WebApplicationContext;

import lombok.extern.slf4j.Slf4j;

@ComponentScan(basePackages = "edu.lehigh.libraries.purchase_request")
@SpringBootApplication
@Slf4j
public class LostItemsApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(LostItemsApplication.class);
	}

	@Override
	protected WebApplicationContext run(SpringApplication application) {
		WebApplicationContext context = super.run(application);
		reportBuild(context);
		return context;
	}

	private static void reportBuild(ApplicationContext context) {
		BuildProperties buildProperties = context.getBean(BuildProperties.class);
		log.info("Build time: " + buildProperties.getTime());
	}

	public static void main(String[] args) {
		log.info("Starting the Lost Items Application");
		ApplicationContext context = SpringApplication.run(LostItemsApplication.class, args);
		reportBuild(context);
		SpringApplication.run(LostItemsApplication.class, args);
        log.info("Lost Items Application started");
	}

}
