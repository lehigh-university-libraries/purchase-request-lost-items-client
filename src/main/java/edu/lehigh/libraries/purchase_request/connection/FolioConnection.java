package edu.lehigh.libraries.purchase_request.connection;

import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;

import java.net.URI;
import java.nio.charset.StandardCharsets;

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
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FolioConnection {

    private static final String LOGIN_PATH = "/authn/login";

    private static final String TENANT_HEADER = "x-okapi-tenant";
    private static final String TOKEN_HEADER = "x-okapi-token";

    // Limit to use, with offsets, for queries that would otherwise fail.
    // Queries that cause dependent joins can fail if the dependent query
    // string is too large for the URL limit.
    private static final Integer LARGE_QUERY_LIMIT = Integer.valueOf(50);

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

    public JSONArray executeGetForArray(String url, String queryString, Integer limit, String arrayProperty) 
        throws Exception {

        if (limit == null) {
            JSONObject responseObject = executeGet(url, queryString, limit);
            return responseObject.getJSONArray(arrayProperty);
        }
        else {
            JSONArray results = new JSONArray();
            int queryLimit = Integer.min(limit.intValue(), LARGE_QUERY_LIMIT);
            int offset = 0;
            while (offset < limit.intValue()) {
                log.debug("Split query: request batch of " + queryLimit + " results.");
                JSONObject responseObject = executeGet(url, queryString, queryLimit, Integer.valueOf(offset));
                JSONArray queryArray = responseObject.getJSONArray(arrayProperty);
                if (queryArray.length() == 0) {
                    break;
                }
                results.putAll(queryArray);
                offset += queryLimit;
            }
            return results;
        }
    }

    public JSONObject executeGet(String url, String queryString) throws Exception {
        return executeGet(url, queryString, null);
    }

    public JSONObject executeGet(String url, String queryString, Integer limit) throws Exception {
        return executeGet(url, queryString, limit, null);
    }


    public JSONObject executeGet(String url, String queryString, Integer limit, Integer offset)
        throws Exception {
        
        RequestBuilder builder = RequestBuilder.get()
            .setUri(config.getFolio().getOkapiBaseUrl() + url)
            .setHeader(TENANT_HEADER, config.getFolio().getTenantId())
            .setHeader(TOKEN_HEADER, token);
        if (queryString != null) {
            builder.addParameter("query", queryString);
        }
        if (limit != null) {
            builder.addParameter("limit", limit.toString());
        }    
        if (offset != null) {
            builder.addParameter("offset", offset.toString());
        }    
        HttpUriRequest getRequest = builder.build();

        CloseableHttpResponse response;
        response = client.execute(getRequest);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity);
        log.debug("Got response with code " + response.getStatusLine() + " and entity " + response.getEntity());

        JSONObject jsonObject = new JSONObject(responseString);
        return jsonObject;
    }

    public boolean executePut(String url, JSONObject data) throws Exception {
        HttpUriRequest putRequest = RequestBuilder.put()
            .setUri(config.getFolio().getOkapiBaseUrl() + url)
            .setHeader(TENANT_HEADER, config.getFolio().getTenantId())
            .setHeader(TOKEN_HEADER, token)
            .setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
            .setEntity(new StringEntity(data.toString(), StandardCharsets.UTF_8.name()))
            .build();

        CloseableHttpResponse response;
        response = client.execute(putRequest);
        if (response.getStatusLine().getStatusCode() == 204) {
            log.debug("Got successful response to PUT.");
            return true;
        }
        else {
            log.warn("Got response with code " + response.getStatusLine());
            return false;
        }
    }

}
