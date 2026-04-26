package com.example.openaiapiservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;

@Entity
public class ChatCompletion {

    @Id
    private String id;

    private String model;

    @Lob
    private String requestJson;

    @Lob
    private String responseJson;

    private Instant created;

    private boolean canceled = false;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public String getRequestJson() {
        return requestJson;
    }
    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }
    public String getResponseJson() {
        return responseJson;
    }
    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }
    public Instant getCreated() {
        return created;
    }
    public void setCreated(Instant created) {
        this.created = created;
    }
    public boolean isCanceled() {
        return canceled;
    }
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}

