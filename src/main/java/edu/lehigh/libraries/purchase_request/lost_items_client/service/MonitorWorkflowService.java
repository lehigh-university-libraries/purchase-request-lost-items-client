package edu.lehigh.libraries.purchase_request.lost_items_client.service;

import java.util.List;

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

    public MonitorWorkflowService(PropertiesConfig config) throws Exception {
        super(config);

        this.APPROVED_STATUS = config.getWorkflowServer().getApprovedStatus();
        this.DENIED_STATUS = config.getWorkflowServer().getDeniedStatus();

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
        // TODO
    }

    private void handleDenial(PurchaseRequest purchaseRequest) {
        log.info("Purchase denied: " + purchaseRequest);
        // TODO
    }

}
