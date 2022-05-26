package edu.lehigh.libraries.purchase_request.lost_items_client.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;
import edu.lehigh.libraries.purchase_request.model.PurchaseRequest;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MonitorNewLostItemsService extends AbstractLostItemsService {

    private final Integer QUERY_LIMIT;
    private final String[] STATUSES;

    public MonitorNewLostItemsService(PropertiesConfig config) throws Exception {
        super(config);
        log.info("Started MonitorNewLostItemsService.");

        QUERY_LIMIT = config.getFolio().getNewLostItemsLimit();
        STATUSES = config.getFolio().getNewLostItemsStatuses();
    }
    
    @Scheduled(cron = "${lost-items-client.schedule.new-lost-items}")
    public void triggerMonitor() {
        log.debug("Schedule triggered: checking for lost items.");
        List<PurchaseRequest> purchaseRequests = loadNewLostItems();

        if (purchaseRequests.size() > 0) {
            log.info("Sending " + purchaseRequests.size() + " new purchase requests.");
            for (PurchaseRequest purchaseRequest: purchaseRequests) {
                log.info("Requesting replacement purchase: " + purchaseRequest);
                PurchaseRequest savedRequest = workflow.submitRequest(purchaseRequest);
                if (savedRequest != null) {
                    log.debug("Successfully submitted purchase request.");
                    markItemSubmittedToWorkflow(savedRequest);
                }
            }
        }
    }

    private List<PurchaseRequest> loadNewLostItems() {
        String queryString = "("
            + buildStatusPhrase()
            + " not " + buildWorkflowPhrase()
            + ")"
            + " sortby hrid";
        return loadFolioItemsAsPurchaseRequests(queryString, QUERY_LIMIT);
    }

    private String buildStatusPhrase() {
        String phrase = Stream.of(STATUSES)
            .map(status -> "status=\"" + status + "\"")
            .collect(Collectors.joining(" or "));
        return "(" + phrase + ")";
    }
    
    private void markItemSubmittedToWorkflow(PurchaseRequest purchaseRequest) {
        // Mark the item submitted
        JSONObject item = purchaseRequest.getExistingFolioItem();
        JSONArray statisticalCodeIds = item.getJSONArray("statisticalCodeIds");
        statisticalCodeIds.put(IN_WORKFLOW_CODE);

        // Record the PR key
        String key = purchaseRequest.getKey();
        JSONArray notes = item.getJSONArray("notes");
        JSONObject note = new JSONObject();
        note.put("itemNoteTypeId", WORKFLOW_TAG_ITEM_NOTE_TYPE);
        note.put("note", key);
        note.put("staffOnly", true);
        notes.put(note);

        // Update it in FOLIO
        log.debug("Calling FOLIO to mark item as submitted to workflow.");
        updateItemInFolio(purchaseRequest, item);
    }

}
