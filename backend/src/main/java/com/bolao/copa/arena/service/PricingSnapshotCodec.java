package com.bolao.copa.arena.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PricingSnapshotCodec {
    private final ObjectMapper json;
    public PricingSnapshotCodec(ObjectMapper json) { this.json=json; }
    public String encode(PricingAssessment assessment) {
        if(assessment==null)return null;
        try { return json.writeValueAsString(assessment); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Invalid pricing snapshot",failure); }
    }
    public PricingAssessment decode(String value) {
        if(value==null)return null;
        try { return json.readValue(value,PricingAssessment.class); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Invalid persisted pricing snapshot",failure); }
    }
}
