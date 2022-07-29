package edu.lehigh.libraries.purchase_request.lost_items_client.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

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
    private final boolean FOLIO_PATRON_REQUESTING_ONLY;
    private final String FOLIO_ITEM_NOTE_LEGACY_CIRCULATION_COUNT;
    
    private Map<String, String> retentionAgreementCodes;

    public MonitorNewLostItemsService(PropertiesConfig config) throws Exception {
        super(config);
        log.info("Started MonitorNewLostItemsService.");

        QUERY_LIMIT = config.getFolio().getNewLostItemsLimit();
        STATUSES = config.getFolio().getNewLostItemsStatuses();
        FOLIO_PATRON_REQUESTING_ONLY = config.getFolio().isNewLostItemsPatronRequestingOnly();
        FOLIO_ITEM_NOTE_LEGACY_CIRCULATION_COUNT = config.getFolio().getItemNotes().getLegacyCirculationCount();
    }

    @PostConstruct
    private void initRetentionAgreementCodes() {
        log.debug("Initializing retention agreement codes.");
        final String RETENTION_AGREEMENT_STATISTICAL_CODE_TYPE = this.config.getFolio().getStatisticalCodeTypeRetentionAgreement();
        this.retentionAgreementCodes = new HashMap<String, String>();

        String url = "/statistical-codes";
        String queryString = "statisticalCodeTypeId=" + RETENTION_AGREEMENT_STATISTICAL_CODE_TYPE;
        JSONObject response;
        try {
            response = folio.executeGet(url, queryString);
        }
        catch (Exception e) {
            log.error("Could not get statistical codes", e);
            return;
        }
        JSONArray statisticalCodes = response.getJSONArray("statisticalCodes");
        for (Object statisticalCodeObject : statisticalCodes) {
            JSONObject statisticalCode = (JSONObject)statisticalCodeObject;
            this.retentionAgreementCodes.put(statisticalCode.getString("id"), statisticalCode.getString("name"));
            log.debug("... loaded retention agreement code: " + statisticalCode.getString("name"));
        }
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
            + buildPatronRequestingPhrase()
            + " not " + buildWorkflowPhrase()
            + ")"
            + " and discoverySuppress=false" 
            + " sortby hrid";
        return loadFolioItemsAsPurchaseRequests(queryString, QUERY_LIMIT);
    }

    private String buildStatusPhrase() {
        String phrase = Stream.of(STATUSES)
            .map(status -> "status=\"" + status + "\"")
            .collect(Collectors.joining(" or "));
        return "(" + phrase + ")";
    }

    private String buildPatronRequestingPhrase() {
        if (FOLIO_PATRON_REQUESTING_ONLY) {
            return " and (notes=\"" + FOLIO_ITEM_NOTE_WORKFLOW_PATRON_REQUESTING + "\")";
        }
        else {
            return "";
        }
    }

    @Override
    void parseItemAdditionalFields(PurchaseRequest purchaseRequest, JSONObject item) {
        parseCirculationCounts(purchaseRequest, item);
        parseRetentionAgreements(purchaseRequest, item);
        parseInstanceRecord(purchaseRequest, item);
    }

    private void parseCirculationCounts(PurchaseRequest purchaseRequest, JSONObject item) {
        parseFolioCirculationCount(purchaseRequest, item);
        parseLegacyCirculationCount(purchaseRequest, item);
    }

    private void parseFolioCirculationCount(PurchaseRequest purchaseRequest, JSONObject item) {
        try {
            String itemId = item.getString("id");
            String url = "/audit-data/circulation/logs";
            String queryString = "(items==\"*" + itemId + "*\" and action==\"Checked out\")";
            JSONObject loansResult = folio.executeGet(url, queryString, 0);
            int count = loansResult.getInt("totalRecords");
            purchaseRequest.setRequesterComments(purchaseRequest.getRequesterComments() + 
                " \n FOLIO Circulation Count: " + count + ".");
        }
        catch (Exception e) {
            log.error("Could not get FOLIO circulation count from item record.", e);
        }
    }

    private void parseLegacyCirculationCount(PurchaseRequest purchaseRequest, JSONObject item) {
        if (FOLIO_ITEM_NOTE_LEGACY_CIRCULATION_COUNT == null) {
            return;
        }
        JSONArray notes = item.getJSONArray("notes");
        for (Object noteObject: notes) {
            JSONObject note = (JSONObject)noteObject;
            String typeId = note.getString("itemNoteTypeId");
            if (FOLIO_ITEM_NOTE_LEGACY_CIRCULATION_COUNT.equals(typeId)) {
                String count = note.getString("note");
                purchaseRequest.setRequesterComments(purchaseRequest.getRequesterComments() + 
                " \n Legacy Circulation Count: " + count + ".");
                return;
            }
        }
        purchaseRequest.setRequesterComments(purchaseRequest.getRequesterComments() + 
        " \n No Legacy Circulation Count found.");
}

    private void parseRetentionAgreements(PurchaseRequest purchaseRequest, JSONObject item) {
        JSONArray statisticalCodes = item.getJSONArray("statisticalCodeIds");
        for (Object statisticalCodeObject : statisticalCodes) {
            String statisticalCode = (String)statisticalCodeObject;
            if (retentionAgreementCodes.containsKey(statisticalCode)) {
                String name = retentionAgreementCodes.get(statisticalCode);
                purchaseRequest.setRequesterComments(purchaseRequest.getRequesterComments() + 
                    " \n Note Retention Agreement: " + name + ".");
            }
        }
    }

    private void parseInstanceRecord(PurchaseRequest purchaseRequest, JSONObject item) {
        // index title from instance record
        try {
            String holdingsRecordId = item.getString("holdingsRecordId");
            String url = "/holdings-storage/holdings/" + holdingsRecordId;
            JSONObject holdingsRecord = folio.executeGet(url, null);
            String instanceRecordId= holdingsRecord.getString("instanceId");
            url = "/inventory/instances/" + instanceRecordId;
            JSONObject instanceRecord = folio.executeGet(url, null);
            if (instanceRecord.has("indexTitle")) {
                String indexTitle = instanceRecord.getString("indexTitle");
                purchaseRequest.setTitle(indexTitle);
                log.debug("using indexTitle: " + indexTitle);
            }
        }
        catch (Exception e) {
            log.error("Could not get index title from instance record.", e);
        }
    }

    private void markItemSubmittedToWorkflow(PurchaseRequest purchaseRequest) {
        // Mark the item submitted
        JSONObject item = purchaseRequest.getExistingFolioItem();
        JSONArray statisticalCodeIds = item.getJSONArray("statisticalCodeIds");
        statisticalCodeIds.put(FOLIO_CODE_IN_WORKFLOW);

        // Record the PR key
        String key = purchaseRequest.getKey();
        JSONArray notes = item.getJSONArray("notes");
        JSONObject note = new JSONObject();
        note.put("itemNoteTypeId", FOLIO_ITEM_NOTE_WORKFLOW_TAG);
        note.put("note", key);
        note.put("staffOnly", true);
        notes.put(note);

        // Update it in FOLIO
        log.debug("Calling FOLIO to mark item as submitted to workflow.");
        updateItemInFolio(purchaseRequest, item);
    }

}
