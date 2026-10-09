package com.bolao.copa.arena.service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Gaussian-regularized map/game-count likelihood. Inputs are IDs/results, never team names or popularity. */
public final class BradleyTerryStrengthModel {
    public record Result(String id,String sport,String home,String away,int homeScore,int awayScore,int bestOf,
            Instant startsAt,Instant finishedAt,Instant confirmedAt,long championship) { }
    public record Estimate(double mapProbability,String source,String confidence,int homeSamples,int awaySamples,
            int headToHeadSamples,List<String> limitations) {
        public static Estimate neutral(String reason) { return new Estimate(0.5,"SYMMETRIC_PRIOR","LOW",0,0,0,List.of(reason)); }
    }
    private record Edge(int h,int a,double homeWins,double games) { }
    private final Map<String,Integer> ids;
    private final double[] strengths;
    private final int[] samples;
    private final int[] components;
    private final List<Result> results;
    public BradleyTerryStrengthModel(List<Result> supplied,Instant asOf,double precision,double halfLifeDays) {
        if(!Double.isFinite(precision)||precision<=0||!Double.isFinite(halfLifeDays)||halfLifeDays<=0) throw new IllegalArgumentException("Invalid prior");
        results=supplied.stream().filter(r->valid(r)&&!r.finishedAt().isAfter(asOf)).toList();
        if(results.stream().map(Result::sport).distinct().count()>1)throw new IllegalArgumentException("Each model must use a single sport");
        var labels=new TreeSet<String>();results.forEach(r->{labels.add(r.home());labels.add(r.away());});
        ids=new HashMap<>();labels.forEach(id->ids.put(id,ids.size()));
        strengths=new double[ids.size()];samples=new int[ids.size()];components=new int[ids.size()];
        double[] exposure=new double[ids.size()];for(int i=0;i<components.length;i++)components[i]=i;
        var edges=new ArrayList<Edge>();
        for(var r:results) {
            int h=ids.get(r.home()),a=ids.get(r.away());
            double days=Math.max(0,ChronoUnit.DAYS.between(r.finishedAt().atOffset(ZoneOffset.UTC).toLocalDate(),asOf.atOffset(ZoneOffset.UTC).toLocalDate()));
            double w=Math.exp(-Math.log(2)*days/halfLifeDays),games=w*(r.homeScore()+r.awayScore());
            edges.add(new Edge(h,a,w*r.homeScore(),games));exposure[h]+=games;exposure[a]+=games;samples[h]++;samples[a]++;
            components[root(h)]=root(a);
        }
        // Diagonal preconditioning bounds the weighted graph-Laplacian Hessian.
        // Synchronous updates make fit independent of ID/name ordering.
        for(int iteration=0;iteration<2000;iteration++) {
            double[] gradient=new double[ids.size()];for(int i=0;i<gradient.length;i++)gradient[i]=-precision*strengths[i];
            for(var e:edges) {double error=e.homeWins()-e.games()*logistic(strengths[e.h()]-strengths[e.a()]);gradient[e.h()]+=error;gradient[e.a()]-=error;}
            double delta=0;
            for(int i=0;i<strengths.length;i++) {double step=gradient[i]/(precision+exposure[i]/2);strengths[i]+=step;delta=Math.max(delta,Math.abs(step));}
            if(delta<1e-10)break;
        }
    }
    public Estimate estimate(String home,String away,int minimumSeries) {
        Integer h=ids.get(home),a=ids.get(away);
        int hn=h==null?0:samples[h],an=a==null?0:samples[a];
        int direct=(int)results.stream().filter(r->(r.home().equals(home)&&r.away().equals(away))||(r.home().equals(away)&&r.away().equals(home))).count();
        var limits=new ArrayList<String>(List.of("MATCH_ROSTERS_UNVERIFIED","MAP_SPECIFIC_DATA_UNAVAILABLE","MODEL_NOT_CALIBRATED"));
        if(h==null||a==null||hn<minimumSeries||an<minimumSeries) {
            limits.add("INSUFFICIENT_INDEPENDENT_SERIES");return new Estimate(0.5,"SYMMETRIC_PRIOR","LOW",hn,an,direct,limits);
        }
        if(root(h)!=root(a)) {limits.add("DISCONNECTED_OPPONENT_GRAPH");return new Estimate(0.5,"SYMMETRIC_PRIOR","LOW",hn,an,direct,limits);}
        return new Estimate(logistic(strengths[h]-strengths[a]),"PANDASCORE_CONFIRMED_RESULTS",Math.min(hn,an)<15?"LOW":"MODERATE",hn,an,direct,limits);
    }
    private int root(int id) { while(components[id]!=id)id=components[id];return id; }
    public static double logistic(double value) { return value>=0?1/(1+Math.exp(-value)):Math.exp(value)/(1+Math.exp(value)); }
    public static boolean valid(Result r) {
        if(r==null||r.id()==null||r.home()==null||r.away()==null||r.home().equals(r.away())||r.finishedAt()==null||!List.of(1,3,5).contains(r.bestOf()))return false;
        int target=r.bestOf()/2+1;return Math.max(r.homeScore(),r.awayScore())==target&&Math.min(r.homeScore(),r.awayScore())>=0&&Math.min(r.homeScore(),r.awayScore())<target;
    }
}
