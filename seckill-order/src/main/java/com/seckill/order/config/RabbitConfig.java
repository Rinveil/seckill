package com.seckill.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seckill.common.mq.OrderMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderProperties.class)
public class RabbitConfig {

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(OrderMqConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreateQueue() {
        return new Queue(OrderMqConstants.QUEUE_CREATE, true);
    }

    @Bean
    public Binding orderCreateBinding(Queue orderCreateQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreateQueue)
                .to(orderExchange)
                .with(OrderMqConstants.ROUTING_KEY_CREATE);
    }

    /** 延迟队列：无消费者，消息 TTL 到期后进入死信 → 过期队列。 */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(OrderMqConstants.QUEUE_DELAY)
                .deadLetterExchange(OrderMqConstants.EXCHANGE)
                .deadLetterRoutingKey(OrderMqConstants.ROUTING_KEY_EXPIRE)
                .build();
    }

    @Bean
    public Binding orderDelayBinding(Queue orderDelayQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderDelayQueue)
                .to(orderExchange)
                .with(OrderMqConstants.ROUTING_KEY_DELAY);
    }

    @Bean
    public Queue orderExpireQueue() {
        return new Queue(OrderMqConstants.QUEUE_EXPIRE, true);
    }

    @Bean
    public Binding orderExpireBinding(Queue orderExpireQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderExpireQueue)
                .to(orderExchange)
                .with(OrderMqConstants.ROUTING_KEY_EXPIRE);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
