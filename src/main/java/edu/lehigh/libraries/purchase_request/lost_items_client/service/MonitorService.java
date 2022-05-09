package edu.lehigh.libraries.purchase_request.lost_items_client.service;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import edu.lehigh.libraries.purchase_request.connection.FolioConnection;
import edu.lehigh.libraries.purchase_request.connection.WorkflowConnection;
import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;
import edu.lehigh.libraries.purchase_request.model.PurchaseRequest;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MonitorService {

    private final String LOST_CODE;
    private final String IN_WORKFLOW_CODE;
    private final String WORKFLOW_TAG_ITEM_NOTE_TYPE;

    @Autowired
    private FolioConnection folio;

    @Autowired
    private WorkflowConnection workflow;

    public MonitorService(PropertiesConfig config) throws Exception {
        log.info("Starting MonitorService.");

        this.LOST_CODE = config.getFolio().getStatisticalCodes().getLost();
        this.IN_WORKFLOW_CODE = config.getFolio().getStatisticalCodes().getInWorkflow();
        this.WORKFLOW_TAG_ITEM_NOTE_TYPE = config.getFolio().getItemNotes().getLostItemWorkflowTag();
    }
    
    @Scheduled(cron = "${lost-items-client.schedule}")
    public void triggerMonitor() {
        log.debug("Schedule triggered: checking for lost items.");
        List<PurchaseRequest> purchaseRequests = checkForNewLostItems();

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
    
    private List<PurchaseRequest> checkForNewLostItems() {
        List<PurchaseRequest> purchaseRequests = new ArrayList<PurchaseRequest>();
        String url = "/inventory/items";
        String queryString = "query=("
            + "(statisticalCodeIds=" + LOST_CODE + ") "
            + "not (statisticalCodeIds=" + IN_WORKFLOW_CODE + ")"
            + ")";
        try {
            JSONObject responseObject = folio.executeGet(url, queryString);
            JSONArray items = responseObject.getJSONArray("items");
            for (Object itemObject: items) {
                JSONObject item = (JSONObject)itemObject;
                PurchaseRequest purchaseRequest = parseItem(item);
                purchaseRequests.add(purchaseRequest);
            }
        }
        catch (Exception e) {
            log.error("Exception querying for lost items: ", e);
        }
        return purchaseRequests;
    }

    private PurchaseRequest parseItem(JSONObject item) {
        PurchaseRequest purchaseRequest = new PurchaseRequest();

        String title = item.getString("title");
        purchaseRequest.setTitle(title);

        JSONArray contributorNames = item.getJSONArray("contributorNames");
        String contributor = contributorNames.getJSONObject(0).getString("name");
        purchaseRequest.setContributor(contributor);

        String barcode = item.getString("barcode");
        purchaseRequest.setRequesterComments("Lost Item.  Barcode: " + barcode);

        String id = item.getString("id");
        purchaseRequest.setExistingFolioItemId(id);

        purchaseRequest.setExistingFolioItem(item);

        return purchaseRequest;
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
        String url = "/inventory/items/" + purchaseRequest.getExistingFolioItemId();
        try {
            boolean success = folio.executePut(url, item);
            if (success) {
                log.debug("Successfully updated FOLIO item.");
            }
            else {
                log.warn("Failed to update FOLIO item as submitted to workflow.");
            }
        }
        catch (Exception e) {
            log.error("Exception updating FOLIO for lost items: ", e);
        }
    }

}
