package com.seckill.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.mq.OrderMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "seckill.mq", name = "enabled", havingValue = "true")
public class RabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(OrderMqConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(OrderMqConstants.QUEUE_CREATE)
                .withArgument("x-dead-letter-exchange", OrderMqConstants.EXCHANGE)
                .withArgument("x-dead-letter-routing-key", OrderMqConstants.ROUTING_KEY_CREATE_DLQ)
                .build();
    }

    @Bean
    public Queue orderCreateDlq() {
        return QueueBuilder.durable(OrderMqConstants.QUEUE_CREATE_DLQ).build();
    }

    @Bean
    public Binding orderCreateBinding(Queue orderCreateQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreateQueue).to(orderExchange).with(OrderMqConstants.ROUTING_KEY_CREATE);
    }

    @Bean
    public Binding orderCreateDlqBinding(Queue orderCreateDlq, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreateDlq).to(orderExchange).with(OrderMqConstants.ROUTING_KEY_CREATE_DLQ);
    }
}
