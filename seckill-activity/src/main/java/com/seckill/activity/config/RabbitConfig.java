package com.seckill.activity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seckill.common.mq.ActivityMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange activityExchange() {
        return new DirectExchange(ActivityMqConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue activityDelayQueue() {
        return QueueBuilder.durable(ActivityMqConstants.QUEUE_DELAY)
                .deadLetterExchange(ActivityMqConstants.EXCHANGE)
                .deadLetterRoutingKey(ActivityMqConstants.ROUTING_KEY_EXPIRE)
                .build();
    }

    @Bean
    public Binding activityDelayBinding(Queue activityDelayQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityDelayQueue)
                .to(activityExchange)
                .with(ActivityMqConstants.ROUTING_KEY_DELAY);
    }

    @Bean
    public Queue activityExpireQueue() {
        return new Queue(ActivityMqConstants.QUEUE_EXPIRE, true);
    }

    @Bean
    public Binding activityExpireBinding(Queue activityExpireQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityExpireQueue)
                .to(activityExchange)
                .with(ActivityMqConstants.ROUTING_KEY_EXPIRE);
    }

    @Bean
    public Queue activityExpireDlq() {
        return new Queue(ActivityMqConstants.QUEUE_EXPIRE_DLQ, true);
    }

    @Bean
    public Binding activityExpireDlqBinding(Queue activityExpireDlq, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityExpireDlq)
                .to(activityExchange)
                .with(ActivityMqConstants.ROUTING_KEY_EXPIRE_DLQ);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
