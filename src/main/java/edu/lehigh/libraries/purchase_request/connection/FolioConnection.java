package edu.lehigh.libraries.purchase_request.connection;

import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;

import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FolioConnection {

    private static final String LOGIN_PATH = "/authn/login";

    private static final String TENANT_HEADER = "x-okapi-tenant";
    private static final String TOKEN_HEADER = "x-okapi-token";

    private final PropertiesConfig config;

    private CloseableHttpClient client;
    private String token;

    public FolioConnection(PropertiesConfig config) throws Exception {
        this.config = config;

        initConnection();
        initToken();

        log.debug("FOLIO connection ready");
    }

    private void initConnection() {
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY, 
            new UsernamePasswordCredentials(config.getFolio().getUsername(), config.getFolio().getPassword()));
        client = HttpClientBuilder.create()
            .setDefaultCredentialsProvider(provider)
            .build();                
    }

    private void initToken() throws Exception {
        String url = config.getFolio().getOkapiBaseUrl() + LOGIN_PATH;
        URI uri = new URIBuilder(url).build();

        JSONObject postData = new JSONObject();
        postData.put("username", config.getFolio().getUsername());
        postData.put("password", config.getFolio().getPassword());
        postData.put("tenant", config.getFolio().getTenantId());

        HttpUriRequest post = RequestBuilder.post()
            .setUri(uri)
            .setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
            .setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()).setVersion(HttpVersion.HTTP_1_1)
            .setHeader(TENANT_HEADER, config.getFolio().getTenantId())
            .setEntity(new StringEntity(postData.toString()))
            .build();
        CloseableHttpResponse response = client.execute(post);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity);
        int responseCode = response.getStatusLine().getStatusCode();
        token = response.getFirstHeader(TOKEN_HEADER).getValue();

        log.debug("got auth response from folio with response code: " + responseCode);

        if (responseCode > 399) {
            throw new Exception(responseString);
        }
    }

    public JSONObject executeGet(String url, String queryString) throws Exception {
        HttpUriRequest getRequest = RequestBuilder.get()
            .setUri(config.getFolio().getOkapiBaseUrl() + url)
            .setHeader(TENANT_HEADER, config.getFolio().getTenantId())
            .setHeader(TOKEN_HEADER, token)
            .addParameter("query", queryString)
            .build();

        CloseableHttpResponse response;
        response = client.execute(getRequest);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity);
        log.debug("Got response with code " + response.getStatusLine() + " and entity " + response.getEntity());

        JSONObject jsonObject = new JSONObject(responseString);
        return jsonObject;
    }

}
