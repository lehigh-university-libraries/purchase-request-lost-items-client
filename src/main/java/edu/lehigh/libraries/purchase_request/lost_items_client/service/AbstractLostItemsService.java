package edu.lehigh.libraries.purchase_request.lost_items_client.service;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import edu.lehigh.libraries.purchase_request.connection.FolioConnection;
import edu.lehigh.libraries.purchase_request.connection.WorkflowConnection;
import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;
import edu.lehigh.libraries.purchase_request.model.PurchaseRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class AbstractLostItemsService {

    final String FOLIO_CODE_IN_WORKFLOW;
    final String FOLIO_ITEM_NOTE_WORKFLOW_TAG;
    final String FOLIO_ITEM_NOTE_WORKFLOW_PATRON_REQUESTING;

    PropertiesConfig config;

    @Autowired
    FolioConnection folio;

    @Autowired
    WorkflowConnection workflow;
 
    AbstractLostItemsService(PropertiesConfig config) {
        this.config = config;
        this.FOLIO_CODE_IN_WORKFLOW = config.getFolio().getStatisticalCodeInWorkflow();
        this.FOLIO_ITEM_NOTE_WORKFLOW_TAG = config.getFolio().getItemNotes().getLostItemWorkflowTag();
        this.FOLIO_ITEM_NOTE_WORKFLOW_PATRON_REQUESTING = config.getFolio().getItemNotes().getLostItemWorkflowPatronRequesting();
    }
    
    List<PurchaseRequest> loadFolioItemsAsPurchaseRequests(String queryString, Integer limit) { 
        log.debug("query string: " + queryString);
        String url = "/inventory/items";
        List<PurchaseRequest> purchaseRequests = new ArrayList<PurchaseRequest>();
        try {
            JSONArray items = folio.executeGetForArray(url, queryString, limit, "items");
            log.debug("Found " + items.length() + " results.");
            for (Object itemObject: items) {
                try {
                    JSONObject item = (JSONObject)itemObject;
                    PurchaseRequest purchaseRequest = parseItem(item);
                    parseItemAdditionalFields(purchaseRequest, item);
                    purchaseRequests.add(purchaseRequest);
                }
                catch (Exception e) {
                    log.error("Exception parsing lost item: ", e);
                }
            }
        }
        catch (Exception e) {
            log.error("Exception querying for lost items: ", e);
        }
        return purchaseRequests;
    }

    void parseItemAdditionalFields(PurchaseRequest purchaseRequest, JSONObject item) {}

    String buildWorkflowPhrase() {
        return " (statisticalCodeIds=" + FOLIO_CODE_IN_WORKFLOW + ") ";
    }

    JSONObject getHoldingRecord(String id) { 
        log.debug("Loading holding record: " + id);
        String url = "/holdings-storage/holdings/" + id;
        JSONObject holding;
        try {
            holding = folio.executeGet(url, null);
        }
        catch (Exception e) {
            log.error("Exception querying for holding: ", e);
            return null;
        }
        return holding;
    }

    JSONObject getInstance(String id) { 
        log.debug("Loading instance: " + id);
        String url = "/inventory/instances/" + id;
        JSONObject holding;
        try {
            holding = folio.executeGet(url, null);
        }
        catch (Exception e) {
            log.error("Exception querying for instance: ", e);
            return null;
        }
        return holding;
    }

    private PurchaseRequest parseItem(JSONObject item) {
        PurchaseRequest purchaseRequest = new PurchaseRequest();

        String title = item.getString("title");
        purchaseRequest.setTitle(title);

        JSONArray contributorNames = item.getJSONArray("contributorNames");
        if (contributorNames.length() > 0) {
            String contributor = contributorNames.getJSONObject(0).getString("name");
            purchaseRequest.setContributor(contributor);
        }

        String barcode = item.getString("barcode");
        purchaseRequest.setRequesterComments("Lost Item.  Barcode: " + barcode);

        JSONArray notes = item.getJSONArray("notes");
        for (Object noteObject: notes) {
            JSONObject note = (JSONObject)noteObject;
            String typeId = note.getString("itemNoteTypeId");
            if (FOLIO_ITEM_NOTE_WORKFLOW_TAG.equals(typeId)) {
                purchaseRequest.setKey(note.getString("note"));
            }
            if (FOLIO_ITEM_NOTE_WORKFLOW_PATRON_REQUESTING.equals(typeId)) {
                purchaseRequest.setRequesterUsername(note.getString("note"));
            }
        }

        String callNumber = item.optString("callNumber");
        if (callNumber.length() == 0) {
            JSONObject effectiveCallNumberComponents = item.getJSONObject("effectiveCallNumberComponents");
            callNumber = effectiveCallNumberComponents.optString("callNumber");
            if (callNumber.length() == 0) {
                callNumber = null;
            }
        }
        purchaseRequest.setCallNumber(callNumber);

        String id = item.getString("id");
        purchaseRequest.setExistingFolioItemId(id);

        purchaseRequest.setExistingFolioItem(item);

        log.debug("Parsed item with title: " + title);
        return purchaseRequest;
    }

    void updateItemInFolio(PurchaseRequest purchaseRequest, JSONObject item) {
        String url = "/inventory/items/" + purchaseRequest.getExistingFolioItemId();
        try {
            boolean success = folio.executePut(url, item);
            if (success) {
                log.info("Successfully updated FOLIO item.");
            }
            else {
                log.warn("Failed to update FOLIO item.");
            }
        }
        catch (Exception e) {
            log.error("Exception updating FOLIO for lost items: ", e);
        }
    }

    void updateHoldingInFolio(JSONObject holding) {
        String url = "/holdings-storage/holdings/" + holding.getString("id");
        try {
            boolean success = folio.executePut(url, holding);
            if (success) {
                log.info("Successfully updated FOLIO holding.");
            }
            else {
                log.warn("Failed to update FOLIO holding.");
            }
        }
        catch (Exception e) {
            log.error("Exception updating FOLIO holding: ", e);
        }
    }

    void updateInstanceInFolio(JSONObject instance) {
        String url = "/inventory/instances/" + instance.getString("id");
        try {
            boolean success = folio.executePut(url, instance);
            if (success) {
                log.info("Successfully updated FOLIO instance.");
            }
            else {
                log.warn("Failed to update FOLIO instance.");
            }
        }
        catch (Exception e) {
            log.error("Exception updating FOLIO instance: ", e);
        }
    }

    JSONArray getItemsForHoldingId(String id) { 
        log.debug("Loading items for holding id: " + id);
        String url = "/inventory/items-by-holdings-id";
        String queryString = "holdingsRecordId==" + id;
        JSONArray items;
        try {
            JSONObject responseObject = folio.executeGet(url, queryString);
            items = responseObject.getJSONArray("items");
        }
        catch (Exception e) {
            log.error("Exception querying for lost items: ", e);
            return null;
        }
        return items;
    }

    JSONArray getHoldingsForInstanceId(String id) { 
        log.debug("Loading holdings for instance id: " + id);
        String url = "/holdings-storage/holdings";
        String queryString = "instanceId==" + id;
        JSONArray holdings;
        try {
            JSONObject responseObject = folio.executeGet(url, queryString);
            holdings = responseObject.getJSONArray("holdingsRecords");
        }
        catch (Exception e) {
            log.error("Exception querying for holdings: ", e);
            return null;
        }
        return holdings;
    }

}
