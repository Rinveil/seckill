package com.seckill.activity.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MallSearchProperties.class)
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "seckill.search.elasticsearch", name = "enabled", havingValue = "true")
    public RestClient elasticsearchRestClient(MallSearchProperties properties) {
        return RestClient.builder(HttpHost.create(properties.uris().trim())).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seckill.search.elasticsearch", name = "enabled", havingValue = "true")
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }
}
