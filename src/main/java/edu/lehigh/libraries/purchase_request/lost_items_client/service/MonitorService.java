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

    @Autowired
    private FolioConnection connection;

    @Autowired
    private WorkflowConnection workflow;

    public MonitorService(PropertiesConfig config) throws Exception {
        log.info("Starting MonitorService.");

        this.LOST_CODE = config.getFolio().getStatisticalCodes().getLost();
    }
    
    @Scheduled(cron = "${lost-items-client.schedule}")
    public void triggerMonitor() {
        log.debug("Schedule triggered: checking for lost items.");
        List<PurchaseRequest> purchaseRequests = checkForNewLostItems();

        if (purchaseRequests.size() > 0) {
            log.info("Sending " + purchaseRequests.size() + " new purchase requests.");
            for (PurchaseRequest purchaseRequest: purchaseRequests) {
                submitPurchaseRequest(purchaseRequest);
            }
        }
    }
    
    private List<PurchaseRequest> checkForNewLostItems() {
        List<PurchaseRequest> purchaseRequests = new ArrayList<PurchaseRequest>();
        String url = "/inventory/items";
        String queryString = "query=(statisticalCodeIds=" + LOST_CODE + ")";
        try {
            JSONObject responseObject = connection.executeGet(url, queryString);
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

        return purchaseRequest;
    }

    private void submitPurchaseRequest(PurchaseRequest purchaseRequest) {
        boolean success = workflow.submitRequest(purchaseRequest);
        if (success) {
            log.debug("Successfully submitted request.");
        }
        else {
            log.warn("Failed to submit request.");
        }
    }

}
