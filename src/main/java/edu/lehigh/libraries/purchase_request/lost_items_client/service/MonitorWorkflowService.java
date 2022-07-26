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
    private final String FOLIO_INSTANCE_STATUS_WITHDRAWN;

    public MonitorWorkflowService(PropertiesConfig config) throws Exception {
        super(config);

        this.QUERY_LIMIT = config.getFolio().getWorkflowItemsLimit();
        this.WORKFLOW_APPROVED_STATUS = config.getWorkflowServer().getApprovedStatus();
        this.WORKFLOW_DENIED_STATUS = config.getWorkflowServer().getDeniedStatus();
        this.FOLIO_ITEM_NOTE_WORKFLOW_COMMENT = config.getFolio().getItemNotes().getLostItemWorkflowComment();
        this.FOLIO_INSTANCE_STATUS_WITHDRAWN = config.getFolio().getInstanceStatusWithdrawn();

        log.info("Started MonitorWorkflowService.");
    }

    @Scheduled(cron = "${lost-items-client.schedule.workflow-decisions}")
    public void triggerMonitor() {
        log.debug("Schedule triggered: checking for workflow decisions.");

        List<PurchaseRequest> purchaseRequests = checkFolioForItemsInWorkflow();
        for (PurchaseRequest purchaseRequest : purchaseRequests) {
            purchaseRequest = updateFromWorkflow(purchaseRequest);
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
    }

    private List<PurchaseRequest> checkFolioForItemsInWorkflow() {
        String queryString = buildWorkflowPhrase();
        return loadFolioItemsAsPurchaseRequests(queryString, QUERY_LIMIT);
    }

    private PurchaseRequest updateFromWorkflow(PurchaseRequest purchaseRequest) {
        log.debug("Updating from workflow: " + purchaseRequest.getKey());
        PurchaseRequest savedRequest = workflow.getPurchaseRequest(purchaseRequest.getKey());
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
        withdrawItem(item, purchaseRequest, true);
        maybeShadowHoldingAndInstance(item);
    }

    private void handleDenial(PurchaseRequest purchaseRequest) {
        log.info("Purchase denied: " + purchaseRequest);

        JSONObject item = purchaseRequest.getExistingFolioItem();
        withdrawItem(item, purchaseRequest, false);
        maybeShadowHoldingAndInstance(item);
    }

    private void withdrawItem(JSONObject item, PurchaseRequest purchaseRequest, boolean purchaseApproved) {
        setItemStatus(item, "Withdrawn");
        setSuppressDiscovery(item, true);
        removeStatisticalCode(item);
        removeStatusNote(item);
        if (purchaseApproved) {
            notePurchaseApproved(item, purchaseRequest);
        }
        else {
            notePurchaseDenied(item, purchaseRequest);
        }
        addCirculationNote(item);
        updateItemInFolio(purchaseRequest, item);
    }

    private void maybeShadowHoldingAndInstance(JSONObject item) {
        // Shadow the holdings record if appropriate
        String holdingRecordId = item.getString("holdingsRecordId");
        if (!hasUnsuppressedItems(holdingRecordId)) {
            log.debug("Shadow the holdings record.");
            JSONObject holdingRecord = getHoldingRecord(holdingRecordId);
            setSuppressDiscovery(holdingRecord, true);
            updateHoldingInFolio(holdingRecord);

            // Shadow the instance record if appropriate
            String instanceId = holdingRecord.getString("instanceId");
            if (!hasUnsuppressedHoldings(instanceId)) {
                log.debug("Shadow the instance record.");
                JSONObject instance = getInstance(instanceId);
                setSuppressDiscovery(instance, true);
                if (FOLIO_INSTANCE_STATUS_WITHDRAWN != null) {
                    setInstanceStatusWithdrawn(instance);
                }
                updateInstanceInFolio(instance);
            }
        }
    }

    private boolean hasUnsuppressedItems(String holdingsRecordId) {
        log.debug("Checking for unsuppressed items on holdings: " + holdingsRecordId);
        JSONArray itemRecords = getItemsForHoldingId(holdingsRecordId);
        for (Object itemObject: itemRecords) {
            JSONObject item = (JSONObject)itemObject;
            if (!item.getBoolean("discoverySuppress")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnsuppressedHoldings(String instanceId) {
        log.debug("Checking for unsuppressed holdings on instance: " + instanceId);
        JSONArray holdingRecords = getHoldingsForInstanceId(instanceId);
        for (Object holdingObject: holdingRecords) {
            JSONObject holding = (JSONObject)holdingObject;
            if (!holding.has("discoverySuppress") || !holding.getBoolean("discoverySuppress")) {
                return true;
            }
        }
        return false;
    }

    private void setItemStatus(JSONObject item, String statusName) {
        JSONObject status = item.getJSONObject("status");
        status.put("name", statusName);
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

    private void addCirculationNote(JSONObject item) {
        JSONArray circulationNotes = item.getJSONArray("circulationNotes");
        JSONObject note = new JSONObject();
        note.put("note", "Route to cataloging. Item was withdrawn.");
        note.put("noteType", "Check in");
        note.put("staffOnly", true);
        circulationNotes.put(note);
    }

    private void setSuppressDiscovery(JSONObject record, boolean value) {
        record.put("discoverySuppress", value);
    }

    private void setInstanceStatusWithdrawn(JSONObject instance) {
        instance.put("statusId", FOLIO_INSTANCE_STATUS_WITHDRAWN);
    }

}
