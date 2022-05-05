package edu.lehigh.libraries.purchase_request.lost_items_client.config;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix="lost-items-client")
@Validated
@EnableScheduling
@Getter @Setter
public class PropertiesConfig {

    @AssertTrue
    @NotNull
    private Boolean enabled;
    
    /**
     * Cron-format schedule to check FOLIO for lost items.
     */
    private String schedule;

}
