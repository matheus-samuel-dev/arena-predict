package com.bolao.copa.arena.service.provider;

/** A provider snapshot. Missing source fields intentionally remain null. */
public record SportsTeam(String externalId, String name, String acronym, String logoUrl) { }
