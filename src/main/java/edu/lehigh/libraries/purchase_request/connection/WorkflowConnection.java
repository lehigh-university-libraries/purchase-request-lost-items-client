package edu.lehigh.libraries.purchase_request.connection;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

    public PurchaseRequest getPurchaseRequest(String key) {
        HttpEntity<?> entity = new HttpEntity<>(headers);
        URI uri;
        try {
            uri = new URI(BASE_URL + "/purchase-requests/" + key);
        }
        catch (URISyntaxException e) {
            log.error("Bad URL syntax: ", e);
            return null;
        }
        ResponseEntity<PurchaseRequest> responseEntity;
        try {
             responseEntity = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                PurchaseRequest.class);
        }
        catch (HttpClientErrorException.NotFound e) {
            log.warn("PR not found: " + key);
            return null;
        }
        PurchaseRequest result = responseEntity.getBody();
        log.debug("Loaded PR: " + result);
        return result;
    }

}
