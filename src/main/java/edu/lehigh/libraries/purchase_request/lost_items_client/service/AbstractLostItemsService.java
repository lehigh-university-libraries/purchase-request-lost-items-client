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

    final String LOST_CODE;
    final String IN_WORKFLOW_CODE;
    final String WORKFLOW_TAG_ITEM_NOTE_TYPE;

    @Autowired
    FolioConnection folio;

    @Autowired
    WorkflowConnection workflow;
 
    AbstractLostItemsService(PropertiesConfig config) {
        this.LOST_CODE = config.getFolio().getStatisticalCodes().getLost();
        this.IN_WORKFLOW_CODE = config.getFolio().getStatisticalCodes().getInWorkflow();
        this.WORKFLOW_TAG_ITEM_NOTE_TYPE = config.getFolio().getItemNotes().getLostItemWorkflowTag();
    }
    
    List<PurchaseRequest> loadFolioLostItems(Boolean inWorkflow) { 
        log.debug("Loading lost items with inWorkflow: " + inWorkflow);
        List<PurchaseRequest> purchaseRequests = new ArrayList<PurchaseRequest>();
        String url = "/inventory/items";
        String queryString = "query=("
            + "(statisticalCodeIds=" + LOST_CODE + ") ";
        if (inWorkflow != null) {
            if (inWorkflow.booleanValue()) {
                queryString += "and (statisticalCodeIds=" + IN_WORKFLOW_CODE + ") ";
            }
            else {
                queryString += "not (statisticalCodeIds=" + IN_WORKFLOW_CODE + ") ";
            }
        }
        queryString += ")";
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

        JSONArray notes = item.getJSONArray("notes");
        for (Object noteObject: notes) {
            JSONObject note = (JSONObject)noteObject;
            if (WORKFLOW_TAG_ITEM_NOTE_TYPE.equals(note.getString("itemNoteTypeId"))) {
                purchaseRequest.setKey(note.getString("note"));
            }
        }

        String id = item.getString("id");
        purchaseRequest.setExistingFolioItemId(id);

        purchaseRequest.setExistingFolioItem(item);

        return purchaseRequest;
    }

}
