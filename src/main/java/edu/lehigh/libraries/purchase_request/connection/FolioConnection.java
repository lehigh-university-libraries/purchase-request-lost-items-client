package edu.lehigh.libraries.purchase_request.connection;

import edu.lehigh.libraries.purchase_request.lost_items_client.config.PropertiesConfig;

import java.io.IOException;
import java.net.URI;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
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
        client = HttpClientBuilder.create().build();
    }

    private void initToken() throws Exception {
        String url = config.getFolio().getOkapiBaseUrl() + LOGIN_PATH;
        URI uri = new URIBuilder(url).build();

        JSONObject postData = new JSONObject();
        postData.put("username", config.getFolio().getUsername());
        postData.put("password", config.getFolio().getPassword());
        postData.put("tenant", config.getFolio().getTenantId());

        HttpPost post = new HttpPost(uri);
        post.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        post.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        post.setHeader(TENANT_HEADER, config.getFolio().getTenantId());
        post.setEntity(new StringEntity(postData.toString(), ContentType.APPLICATION_JSON));

        client.execute(post, response -> {
            String responseString = EntityUtils.toString(response.getEntity());
            int responseCode = response.getCode();
            token = response.getFirstHeader(TOKEN_HEADER).getValue();

            log.debug("got auth response from folio with response code: " + responseCode);

            if (responseCode > 399) {
                throw new IOException(responseString);
            }
            return null;
        });
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

        URIBuilder builder = new URIBuilder(config.getFolio().getOkapiBaseUrl() + url);
        if (queryString != null) {
            builder.addParameter("query", queryString);
        }
        if (limit != null) {
            builder.addParameter("limit", limit.toString());
        }
        if (offset != null) {
            builder.addParameter("offset", offset.toString());
        }

        HttpGet getRequest = new HttpGet(builder.build());
        getRequest.setHeader(TENANT_HEADER, config.getFolio().getTenantId());
        getRequest.setHeader(TOKEN_HEADER, token);

        return client.execute(getRequest, response -> {
            if (response.getCode() > 399) {
                throw new IOException("Cannot execute request: " + response);
            }

            String responseString = EntityUtils.toString(response.getEntity());
            log.debug("Got response with code " + response.getCode() + " and entity " + response.getEntity());

            return new JSONObject(responseString);
        });
    }

    public boolean executePut(String url, JSONObject data) throws Exception {
        HttpPut putRequest = new HttpPut(config.getFolio().getOkapiBaseUrl() + url);
        putRequest.setHeader(TENANT_HEADER, config.getFolio().getTenantId());
        putRequest.setHeader(TOKEN_HEADER, token);
        putRequest.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        putRequest.setEntity(new StringEntity(data.toString(), ContentType.APPLICATION_JSON));

        return client.execute(putRequest, response -> {
            if (response.getCode() == 204) {
                log.debug("Got successful response to PUT.");
                return true;
            }
            log.warn("Got response with code " + response.getCode());
            return false;
        });
    }

}
