package com.seckill.core.client;

import com.seckill.common.mq.OrderCreateMessage;
import com.seckill.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * MQ 关闭时同步调用 order 建单（集群内 Service DNS，不经网关）。
 */
@Component
public class OrderCreateClient {

    private final RestTemplate restTemplate;
    private final String orderBaseUrl;

    public OrderCreateClient(
            RestTemplate restTemplate,
            @Value("${seckill.order-uri:http://seckill-order:8084}") String orderBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.orderBaseUrl = orderBaseUrl.endsWith("/")
                ? orderBaseUrl.substring(0, orderBaseUrl.length() - 1)
                : orderBaseUrl;
    }

    public void createSync(OrderCreateMessage message) {
        String url = orderBaseUrl + "/api/order/internal/create";
        ResponseEntity<Result<Void>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(message),
                new ParameterizedTypeReference<>() {
                }
        );
        Result<Void> body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.code() != 0) {
            throw new IllegalStateException("sync create order failed: http="
                    + response.getStatusCode().value()
                    + " body=" + body);
        }
    }
}
