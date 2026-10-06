package com.bolao.copa.arena.service.provider.pandascore;

import com.bolao.copa.arena.service.provider.BoundedSportsHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** PandaScore authentication/configuration over the shared bounded transport. */
@Component
public class PandaScoreClient extends BoundedSportsHttpClient {
    @Autowired
    public PandaScoreClient(PandaScoreProperties properties,ObjectMapper json) { super(properties,json); }
    PandaScoreClient(PandaScoreProperties properties,ObjectMapper json,RestClient http,Clock clock) { super(properties,json,http,clock); }
}
