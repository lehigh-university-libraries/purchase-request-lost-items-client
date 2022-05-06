package edu.lehigh.libraries.purchase_request.model;

import org.json.JSONObject;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @EqualsAndHashCode @ToString
public class PurchaseRequest {

    private String key;

    private Long id;

    private String title;
    
    private String contributor;

    private String isbn;

    private String requesterUsername;

    private String librarianUsername;

    private String format;

    private String speed;

    private String destination;

    private String requesterComments;

    private String clientName;

    private String reporterName;

    private String creationDate;

    @ToString.Exclude
    private String existingFolioItemId;

    @ToString.Exclude
    private JSONObject existingFolioItem;

}
