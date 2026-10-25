package com.surf.tracker.main.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Collection;

//Uncomment if debugging is required
//@Component
public class JmsListenerDebugger {

    private static final Logger logger = LoggerFactory.getLogger(JmsListenerDebugger.class);
    private final ApplicationContext context;

    public JmsListenerDebugger(ApplicationContext context) {
        this.context = context;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void debugJmsListeners() {
        try {
            JmsListenerEndpointRegistry registry = context.getBean(JmsListenerEndpointRegistry.class);

            logger.info("=== JMS LISTENER REGISTRY DEBUG ===");

            Collection<MessageListenerContainer> containers = registry.getListenerContainers();
            logger.info("Total JMS listener containers: {}", containers.size());

            for (MessageListenerContainer container : containers) {
                logger.info("Container: {}", container.getClass().getSimpleName());

                if (container instanceof AbstractMessageListenerContainer) {
                    AbstractMessageListenerContainer amlc = (AbstractMessageListenerContainer) container;
                    logger.info("  Destination: {}", amlc.getDestination());
                    logger.info("  Subscription: {}", amlc.getSubscriptionName());
                    logger.info("  PubSubDomain: {}", amlc.isPubSubDomain());
                    logger.info("  Active: {}", amlc.isRunning());

                    // Get the listener method info if available
//                    logger.info("  Bean: {}", amlc.getBeanName());
                }
            }
            logger.info("=== END DEBUG ===");

        } catch (Exception e) {
            logger.error("Failed to debug JMS listeners", e);
        }
    }
}