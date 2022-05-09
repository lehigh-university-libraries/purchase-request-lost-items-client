package edu.lehigh.libraries.purchase_request.connection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;
import edu.lehigh.libraries.purchase_request.model.PurchaseRequest;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WorkflowConnection {

    @Autowired
    private RestTemplate restTemplate;

    private HttpHeaders headers;
    private PropertiesConfig config;

    private String BASE_URL;

    public WorkflowConnection(PropertiesConfig config) {
        this.config = config;
        initHeaders();

        this.BASE_URL = config.getWorkflowServer().getBaseUrl();
    }

    private void initHeaders() {
        this.headers = new HttpHeaders();
        headers.setBasicAuth(
            config.getWorkflowServer().getUsername(),
            config.getWorkflowServer().getPassword());
    }

    public PurchaseRequest submitRequest(PurchaseRequest purchaseRequest) {
        HttpEntity<Object> request = new HttpEntity<Object>(purchaseRequest, headers);
        Object resultObject = restTemplate.postForObject(
            BASE_URL + "/purchase-requests", 
            request,
            PurchaseRequest.class);
        PurchaseRequest result = (PurchaseRequest)resultObject;
        log.debug("Submitted request with result " + result);
        result.setExistingFolioItem(purchaseRequest.getExistingFolioItem());
        result.setExistingFolioItemId(purchaseRequest.getExistingFolioItemId());
        return result;
    }

}
