package com.surf.tracker.main.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import jakarta.jms.ConnectionFactory;

@Configuration
@EnableJms
public class JmsConfig {
    private static final Logger log = LoggerFactory.getLogger(JmsConfig.class);

    @Bean
    public JmsTemplate topicJmsTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setPubSubDomain(true);
        template.setMessageConverter(messageConverter);
        template.setDeliveryPersistent(true);
        return template;
    }

    @Bean
    public JmsListenerContainerFactory<?> topicJmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer
    ) {
        DefaultJmsListenerContainerFactory factory =
                new DefaultJmsListenerContainerFactory();

        factory.setPubSubDomain(true);
        factory.setConcurrency("3-10");

        configurer.configure(factory, connectionFactory);

        factory.setErrorHandler(t ->
                log.error("JMS listener error", t)
        );

        return factory;
    }

    @Bean
    public JmsTemplate queueJmsTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setPubSubDomain(false);
        template.setMessageConverter(messageConverter);
        template.setDeliveryPersistent(true);
        return template;
    }

    @Bean
    public JmsListenerContainerFactory<?> queueJmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer
    ) {
        DefaultJmsListenerContainerFactory factory =
                new DefaultJmsListenerContainerFactory();

        factory.setPubSubDomain(false);
        factory.setConcurrency("1-3");

        configurer.configure(factory, connectionFactory);

        factory.setErrorHandler(t ->
                log.error("Queue JMS listener error", t)
        );

        return factory;
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter =
                new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        converter.setObjectMapper(objectMapper);
        return converter;
    }
}

