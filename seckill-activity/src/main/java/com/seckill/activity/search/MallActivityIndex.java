package com.seckill.activity.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.seckill.activity.domain.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class MallActivityIndex {

    public static final String INDEX = "mall_activity";
    private static final Logger log = LoggerFactory.getLogger(MallActivityIndex.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final String INDEX_BODY = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "max_ngram_diff": 1,
                "analysis": {
                  "analyzer": {
                    "mall_ngram": {
                      "type": "custom",
                      "tokenizer": "mall_ngram_tok",
                      "filter": ["lowercase"]
                    }
                  },
                  "tokenizer": {
                    "mall_ngram_tok": {
                      "type": "ngram",
                      "min_gram": 1,
                      "max_gram": 2
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "id": { "type": "long" },
                  "title": { "type": "text", "analyzer": "mall_ngram", "search_analyzer": "mall_ngram" },
                  "status": { "type": "keyword" },
                  "priceFen": { "type": "integer" },
                  "originPriceFen": { "type": "integer" },
                  "stock": { "type": "integer" },
                  "startAt": { "type": "date" },
                  "endAt": { "type": "date" },
                  "limitPerUser": { "type": "integer" }
                }
              }
            }
            """;

    private final ObjectProvider<ElasticsearchClient> client;
    private final MallSearchProperties properties;

    public MallActivityIndex(ObjectProvider<ElasticsearchClient> client, MallSearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled() && client.getIfAvailable() != null;
    }

    public void ensureIndex() {
        ElasticsearchClient es = requireClient();
        if (es == null) {
            return;
        }
        try {
            boolean exists = es.indices().exists(e -> e.index(INDEX)).value();
            if (exists) {
                return;
            }
            es.indices().create(c -> c.index(INDEX).withJson(new StringReader(INDEX_BODY)));
            log.info("created elasticsearch index {}", INDEX);
        } catch (Exception ex) {
            log.warn("ensure mall index failed", ex);
        }
    }

    public void sync(Activity activity) {
        if (activity == null || activity.getId() == null) {
            return;
        }
        if (isDraft(activity)) {
            delete(activity.getId());
            return;
        }
        upsert(activity);
    }

    public void delete(long id) {
        ElasticsearchClient es = requireClient();
        if (es == null) {
            return;
        }
        try {
            es.delete(d -> d.index(INDEX).id(String.valueOf(id)).refresh(Refresh.WaitFor));
        } catch (Exception ex) {
            log.warn("delete mall index doc failed, id={}", id, ex);
        }
    }

    public void reindex(List<Activity> activities) {
        ElasticsearchClient es = requireClient();
        if (es == null) {
            return;
        }
        ensureIndex();
        int n = 0;
        for (Activity activity : activities) {
            if (activity != null && !isDraft(activity)) {
                upsert(activity);
                n++;
            }
        }
        log.info("reindexed {} mall activities", n);
    }

    public SearchPage search(String keyword, String status, int from, int size) {
        ElasticsearchClient es = requireClient();
        if (es == null) {
            throw new IllegalStateException("elasticsearch disabled");
        }
        try {
            String q = keyword == null ? "" : keyword.trim();
            SearchResponse<MallActivityDocument> resp = es.search(s -> {
                s.index(INDEX).from(from).size(size).trackTotalHits(t -> t.enabled(true));
                s.sort(so -> so.field(f -> f.field("id").order(SortOrder.Desc)));
                s.query(qb -> qb.bool(b -> {
                    if (q.isEmpty()) {
                        b.must(m -> m.matchAll(ma -> ma));
                    } else {
                        b.must(m -> m.match(t -> t.field("title").query(q)));
                    }
                    if (status != null && !status.isBlank()) {
                        b.filter(f -> f.term(t -> t.field("status").value(status)));
                    }
                    return b;
                }));
                if (!q.isEmpty()) {
                    s.highlight(h -> h.fields("title", f -> f.preTags("<em>").postTags("</em>")));
                }
                return s;
            }, MallActivityDocument.class);

            List<HitRow> rows = new ArrayList<>();
            for (Hit<MallActivityDocument> hit : resp.hits().hits()) {
                MallActivityDocument src = hit.source();
                if (src == null) {
                    continue;
                }
                String hl = src.title();
                if (hit.highlight() != null && hit.highlight().get("title") != null
                        && !hit.highlight().get("title").isEmpty()) {
                    hl = hit.highlight().get("title").get(0);
                }
                rows.add(new HitRow(src.id(), hl));
            }
            long total = 0;
            if (resp.hits().total() != null) {
                total = resp.hits().total().value();
            }
            return new SearchPage(rows, total);
        } catch (Exception ex) {
            throw new IllegalStateException("elasticsearch search failed", ex);
        }
    }

    private void upsert(Activity activity) {
        ElasticsearchClient es = requireClient();
        if (es == null) {
            return;
        }
        MallActivityDocument doc = toDoc(activity);
        try {
            es.index(i -> i.index(INDEX).id(String.valueOf(doc.id())).document(doc).refresh(Refresh.WaitFor));
        } catch (Exception ex) {
            log.warn("upsert mall index failed, id={}", doc.id(), ex);
        }
    }

    private MallActivityDocument toDoc(Activity activity) {
        return new MallActivityDocument(
                activity.getId(),
                activity.getTitle(),
                statusName(activity.getStatus()),
                activity.getPriceFen() == null ? 0 : activity.getPriceFen(),
                activity.getOriginPriceFen() == null ? 0 : activity.getOriginPriceFen(),
                activity.getStock() == null ? 0 : activity.getStock(),
                activity.getStartAt() == null ? null : activity.getStartAt().atZone(ZONE).format(ISO),
                activity.getEndAt() == null ? null : activity.getEndAt().atZone(ZONE).format(ISO),
                activity.getLimitPerUser() == null || activity.getLimitPerUser() < 1
                        ? 1 : activity.getLimitPerUser()
        );
    }

    private ElasticsearchClient requireClient() {
        if (!properties.enabled()) {
            return null;
        }
        return client.getIfAvailable();
    }

    private static boolean isDraft(Activity activity) {
        return activity.getStatus() == null || activity.getStatus() == Activity.STATUS_DRAFT;
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return "DRAFT";
        }
        return switch (status) {
            case Activity.STATUS_OPEN -> "OPEN";
            case Activity.STATUS_PREHEATED -> "PREHEATED";
            case Activity.STATUS_CLOSED -> "CLOSED";
            default -> "DRAFT";
        };
    }

    public record HitRow(long id, String highlightedTitle) {
    }

    public record SearchPage(List<HitRow> hits, long total) {
    }
}
