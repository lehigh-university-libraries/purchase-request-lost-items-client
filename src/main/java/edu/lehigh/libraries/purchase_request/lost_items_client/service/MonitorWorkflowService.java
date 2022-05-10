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
    
    private final String APPROVED_STATUS;
    private final String DENIED_STATUS;
    private final String WORKFLOW_COMMENT_ITEM_NOTE_TYPE;

    public MonitorWorkflowService(PropertiesConfig config) throws Exception {
        super(config);

        this.APPROVED_STATUS = config.getWorkflowServer().getApprovedStatus();
        this.DENIED_STATUS = config.getWorkflowServer().getDeniedStatus();
        this.WORKFLOW_COMMENT_ITEM_NOTE_TYPE = config.getFolio().getItemNotes().getLostItemWorkflowComment();

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
        return loadFolioLostItems(Boolean.TRUE);
    }

    private PurchaseRequest updateFromWorkflow(PurchaseRequest purchaseRequest) {
        log.debug("Updating from workflow: " + purchaseRequest.getKey());
        PurchaseRequest savedRequest = workflow.getPurchaseRequest(purchaseRequest.getKey());
        savedRequest.setExistingFolioItem(purchaseRequest.getExistingFolioItem());
        savedRequest.setExistingFolioItemId(purchaseRequest.getExistingFolioItemId());
        return savedRequest;
    }

    private boolean isApproved(PurchaseRequest purchaseRequest) {
        return APPROVED_STATUS.equals(purchaseRequest.getStatus());
    }

    private boolean isDenied(PurchaseRequest purchaseRequest) {
        return DENIED_STATUS.equals(purchaseRequest.getStatus());
    }

    private void handleApproval(PurchaseRequest purchaseRequest) {
        log.info("Purchase approved: " + purchaseRequest);
        JSONObject item = purchaseRequest.getExistingFolioItem();
        setItemStatus(item, "On order");
        removeStatisticalCode(item);
        removeStatusNote(item);
        addDescriptiveNote(item);

        // Update it in FOLIO
        log.debug("Calling FOLIO to mark item as approved.");
        updateItemInFolio(purchaseRequest, item);
    }

    private void handleDenial(PurchaseRequest purchaseRequest) {
        log.info("Purchase denied: " + purchaseRequest);
        // TODO
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
            if (IN_WORKFLOW_CODE.equals(code)) {
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
            if (WORKFLOW_TAG_ITEM_NOTE_TYPE.equals(note.getString("itemNoteTypeId"))) {
                it.remove();
                break;
            }
        }
    }

    private void addDescriptiveNote(JSONObject item) {
        // Add a descriptive note
        JSONObject note = new JSONObject();
        note.put("itemNoteTypeId", WORKFLOW_COMMENT_ITEM_NOTE_TYPE);
        note.put("note", "On [date] [user] selected to purchase this item. Previous status was [status]. [note]");
        note.put("staffOnly", true);
        notes.put(note);        
    }

}
