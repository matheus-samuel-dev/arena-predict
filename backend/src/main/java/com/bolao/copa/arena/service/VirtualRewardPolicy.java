package com.bolao.copa.arena.service;

import java.math.*;

/** Product reward bounds never alter the estimated probability. No money/bookmaker inputs. */
public final class VirtualRewardPolicy {
    public static final BigDecimal MIN=new BigDecimal("1.10"),MAX=new BigDecimal("8.00");
    public static final String VERSION="virtual-inverse-capped-v1";
    public record Reward(BigDecimal uncapped,BigDecimal displayed,boolean limited) { }
    public static Reward convert(double probability,double refund) {
        if(!Double.isFinite(probability)||!Double.isFinite(refund)||probability<0||refund<0||probability+refund>1.000000000001)
            throw new IllegalArgumentException("Invalid probability mass");
        if(probability==0) return new Reward(null,MAX,true); // Impossible options are never accepted.
        BigDecimal raw=BigDecimal.valueOf(Math.max(0,1-refund)).divide(BigDecimal.valueOf(Math.min(1,probability)),MathContext.DECIMAL128);
        BigDecimal value=raw.max(MIN).min(MAX).setScale(2,RoundingMode.HALF_UP);
        return new Reward(raw,value,raw.compareTo(MIN)<0||raw.compareTo(MAX)>0);
    }
}
