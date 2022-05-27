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
    
    private Schedule schedule;
    private Folio folio;
    private WorkflowServer workflowServer;

    @Getter @Setter
    public static class Schedule {

        /**
         * Cron-format schedule to check FOLIO for lost items.
         */
        private String newLostItems;

        /**
         * Cron-format schedule to check the Workflow service for decisions.
         */
        private String workflowDecisions;

    }

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

        /**
         * How many new lost items at a time to load from FOLIO when the schedule is triggered.  Empty for no limit.
         */
        private Integer newLostItemsLimit = null;

        /**
         * Comma-separated list of item status values that should trigger the workflow
         */
        private String[] newLostItemsStatuses;

        /**
         * How many items at a time currently in workflow to load from FOLIO when the schedule is triggered.  Empty for no limit.
         */
        private Integer workflowItemsLimit = null;

        /**
         * UUID of the FOLIO instance status representing a withdrawn record
         */
        private String instanceStatusWithdrawn;

        /**
         * FOLIO UUID for the statistical code for items added to the workflow.
         */
        private String statisticalCodeInWorkflow;

        private ItemNotes itemNotes;

        @Getter @Setter
        public static class ItemNotes {

            /**
             * FOLIO UUID for the item note type for the tag (ID) of a workflow item to replace this lost item.
             */
            private String lostItemWorkflowTag;

            /**
             * FOLIO UUID for the item note type for a comment about replacing this lost item.
             */
            private String lostItemWorkflowComment;

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

        /**
         * Name of the workflow status representing approved purchase requests
         */
        private String approvedStatus;

        /**
         * Name of the workflow status representing denied purchase requests
         */
        private String deniedStatus;

    }

}
