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

    private Folio folio;
    private WorkflowServer workflowServer;

    @Getter @Setter
    public static class Folio {

        /**
         * FOLIO API username
         */
        private String username;

        /**
         * FOLIO API password
         */
        private String password;

        /**
         * FOLIO API tenant ID
         */
        private String tenantId;

        /**
         * FOLIO API base OKAPI url
         */
        private String okapiBaseUrl;

        private StatisticalCodes statisticalCodes;

        @Getter @Setter
        public static class StatisticalCodes {

            /**
             * FOLIO UUID for the statistical code for items confirmed lost.
             */
            private String lost;

        }

    }

    @Getter @Setter
    public static class WorkflowServer {

        /**
         * Workflow Proxy Server base url for API calls
         */
        private String baseUrl;

        /**
         * Workflow Proxy Server username
         */
        private String username;

        /**
         * Workflow Proxy Server password
         */
        private String password;

    }

}
