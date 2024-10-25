package edu.lehigh.libraries.purchase_request.lost_items_client.service;

import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;
import edu.lehigh.libraries.purchase_request.model.PurchaseRequest;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MonitorWorkflowService extends AbstractLostItemsService {
    
    private final Integer QUERY_LIMIT;
    private final String WORKFLOW_APPROVED_STATUS;
    private final String WORKFLOW_DENIED_STATUS;
    private final String FOLIO_ITEM_NOTE_WORKFLOW_COMMENT;

    public MonitorWorkflowService(PropertiesConfig config) throws Exception {
        super(config);

        this.QUERY_LIMIT = config.getFolio().getWorkflowItemsLimit();
        this.WORKFLOW_APPROVED_STATUS = config.getWorkflowServer().getApprovedStatus();
        this.WORKFLOW_DENIED_STATUS = config.getWorkflowServer().getDeniedStatus();
        this.FOLIO_ITEM_NOTE_WORKFLOW_COMMENT = config.getFolio().getItemNotes().getLostItemWorkflowComment();

        log.info("Started MonitorWorkflowService.");
    }

    @Scheduled(cron = "${lost-items-client.schedule.workflow-decisions}")
    public void triggerMonitor() {
        log.debug("Schedule triggered: checking for workflow decisions.");

        List<PurchaseRequest> purchaseRequests = checkFolioForItemsInWorkflow();
        for (PurchaseRequest purchaseRequest : purchaseRequests) {
            try {
                log.debug("Checking for decision on " + purchaseRequest.getKey());
                purchaseRequest = updateFromWorkflow(purchaseRequest);
                if (purchaseRequest == null) {
                    log.debug("PR not found in WorkflowService; skipping.");
                    continue;
                }
                if (isApproved(purchaseRequest)) {
                    handleApproval(purchaseRequest);
                }
                else if (isDenied(purchaseRequest)) {
                    handleDenial(purchaseRequest);
                }
                else {
                    log.debug("No decision yet on request " + purchaseRequest.getKey());
                }
            }
            catch (Exception e) {
                log.warn("Exception handling PR " + purchaseRequest.getKey() + ".  Continuing to others.");
            }
        }
    }

    private List<PurchaseRequest> checkFolioForItemsInWorkflow() {
        String queryString = buildWorkflowPhrase();
        return loadFolioItemsAsPurchaseRequests(queryString, QUERY_LIMIT);
    }

    private PurchaseRequest updateFromWorkflow(PurchaseRequest purchaseRequest) {
        log.debug("Updating from workflow: " + purchaseRequest.getKey());
        PurchaseRequest savedRequest = workflow.getPurchaseRequest(purchaseRequest.getKey());
        if (savedRequest == null) {
            return null;
        }
        savedRequest.setExistingFolioItem(purchaseRequest.getExistingFolioItem());
        savedRequest.setExistingFolioItemId(purchaseRequest.getExistingFolioItemId());
        return savedRequest;
    }

    private boolean isApproved(PurchaseRequest purchaseRequest) {
        return WORKFLOW_APPROVED_STATUS.equals(purchaseRequest.getStatus());
    }

    private boolean isDenied(PurchaseRequest purchaseRequest) {
        return WORKFLOW_DENIED_STATUS.equals(purchaseRequest.getStatus());
    }

    private void handleApproval(PurchaseRequest purchaseRequest) {
        log.info("Purchase approved: " + purchaseRequest);
        JSONObject item = purchaseRequest.getExistingFolioItem();
        markItemDecision(item, purchaseRequest, true);
    }

    private void handleDenial(PurchaseRequest purchaseRequest) {
        log.info("Purchase denied: " + purchaseRequest);

        JSONObject item = purchaseRequest.getExistingFolioItem();
        markItemDecision(item, purchaseRequest, false);
    }

    private void markItemDecision(JSONObject item, PurchaseRequest purchaseRequest, boolean purchaseApproved) {
        removeStatisticalCode(item);
        removeStatusNote(item);
        if (purchaseApproved) {
            notePurchaseApproved(item, purchaseRequest);
        }
        else {
            notePurchaseDenied(item, purchaseRequest);
        }
        updateItemInFolio(purchaseRequest, item);
    }

    private void removeStatisticalCode(JSONObject item) {
        JSONArray statisticalCodeIds = item.getJSONArray("statisticalCodeIds");
        Iterator<?> it = statisticalCodeIds.iterator();
        while (it.hasNext()) {
            String code = (String)it.next();
            if (FOLIO_CODE_IN_WORKFLOW.equals(code)) {
                it.remove();
                break;
            }
        }
    }

    private void removeStatusNote(JSONObject item) {
        JSONArray notes = item.getJSONArray("notes");
        Iterator<?> it = notes.iterator();
        while (it.hasNext()) {
            JSONObject note = (JSONObject)it.next();
            if (FOLIO_ITEM_NOTE_WORKFLOW_TAG.equals(note.getString("itemNoteTypeId"))) {
                it.remove();
                break;
            }
        }
    }

    private void notePurchaseApproved(JSONObject item, PurchaseRequest purchaseRequest) {
        addDescriptiveNote(item,
            "At " + purchaseRequest.getUpdateDate() + " a selector decided to re-purchase this lost item.");
    }

    private void notePurchaseDenied(JSONObject item, PurchaseRequest purchaseRequest) {
        addDescriptiveNote(item, 
            "At " + purchaseRequest.getUpdateDate() + " a selector decided NOT to re-purchase this lost item.");
    }

    private void addDescriptiveNote(JSONObject item, String noteText) {
        JSONArray notes = item.getJSONArray("notes");
        JSONObject note = new JSONObject();
        note.put("itemNoteTypeId", FOLIO_ITEM_NOTE_WORKFLOW_COMMENT);
        note.put("note", noteText);
        note.put("staffOnly", true);
        notes.put(note);        
    }

}
