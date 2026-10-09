package com.bolao.copa.arena.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Probability, evidence quality and points policy have independent meanings and versions. */
public record PricingAssessment(boolean available, Map<String,Double> probabilities,double refundProbability,
        String modelVersion,String evidenceSource,String confidence,String dataRevision,Instant dataAsOf,
        int homeSamples,int awaySamples,int headToHeadSamples,List<String> limitations,
        String rewardPolicy,Map<String,BigDecimal> uncappedMultipliers,Set<String> rewardLimitedOptions) {
    public PricingAssessment {
        probabilities=Map.copyOf(probabilities);limitations=List.copyOf(limitations);
        uncappedMultipliers=Map.copyOf(uncappedMultipliers);rewardLimitedOptions=Set.copyOf(rewardLimitedOptions);
    }
}
