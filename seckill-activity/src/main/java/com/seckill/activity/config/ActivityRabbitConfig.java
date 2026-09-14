package com.seckill.activity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.mq.ActivityMqConstants;
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
public class ActivityRabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public DirectExchange activityExchange() {
        return new DirectExchange(ActivityMqConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue activityDelayQueue() {
        return QueueBuilder.durable(ActivityMqConstants.QUEUE_DELAY)
                .withArgument("x-dead-letter-exchange", ActivityMqConstants.EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ActivityMqConstants.ROUTING_KEY_EXPIRE)
                .build();
    }

    @Bean
    public Queue activityExpireQueue() {
        return QueueBuilder.durable(ActivityMqConstants.QUEUE_EXPIRE)
                .withArgument("x-dead-letter-exchange", ActivityMqConstants.EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ActivityMqConstants.ROUTING_KEY_EXPIRE_DLQ)
                .build();
    }

    @Bean
    public Queue activityExpireDlq() {
        return QueueBuilder.durable(ActivityMqConstants.QUEUE_EXPIRE_DLQ).build();
    }

    @Bean
    public Binding activityDelayBinding(Queue activityDelayQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityDelayQueue).to(activityExchange).with(ActivityMqConstants.ROUTING_KEY_DELAY);
    }

    @Bean
    public Binding activityExpireBinding(Queue activityExpireQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityExpireQueue).to(activityExchange).with(ActivityMqConstants.ROUTING_KEY_EXPIRE);
    }

    @Bean
    public Binding activityExpireDlqBinding(Queue activityExpireDlq, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityExpireDlq).to(activityExchange).with(ActivityMqConstants.ROUTING_KEY_EXPIRE_DLQ);
    }
}
