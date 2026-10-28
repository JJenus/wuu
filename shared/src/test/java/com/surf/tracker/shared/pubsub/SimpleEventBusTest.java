package com.surf.tracker.shared.pubsub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SimpleEventBusTest {

    private SimpleEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new SimpleEventBus();
    }

    @Test
    void testSubscribeAndPublish() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        eventBus.subscribe(TestEvent.class, event -> {
            receivedMessage.set(event.getMessage());
            latch.countDown();
        });

        eventBus.publish(new TestEvent("Hello, World!"));

        boolean messageReceived = latch.await(1, TimeUnit.SECONDS);
        assertTrue(messageReceived);
        assertEquals("Hello, World!", receivedMessage.get());
    }

    @Test
    void testMultipleSubscribers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger messageCount = new AtomicInteger(0);

        eventBus.subscribe(TestEvent.class, event -> {
            messageCount.incrementAndGet();
            latch.countDown();
        });

        eventBus.subscribe(TestEvent.class, event -> {
            messageCount.incrementAndGet();
            latch.countDown();
        });

        eventBus.publish(new TestEvent("Test"));

        boolean allMessagesReceived = latch.await(1, TimeUnit.SECONDS);
        assertTrue(allMessagesReceived);
        assertEquals(2, messageCount.get());
    }

    @Test
    void testUnsubscribe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        EventHandler<TestEvent> handler = event -> {
            messageCount.incrementAndGet();
            latch.countDown();
        };

        eventBus.subscribe(TestEvent.class, handler);
        eventBus.unsubscribe(TestEvent.class, handler);

        eventBus.publish(new TestEvent("Test"));

        boolean noMessageReceived = latch.await(100, TimeUnit.MILLISECONDS);
        assertFalse(noMessageReceived);
        assertEquals(0, messageCount.get());
    }

    @Test
    void testEventTypeSpecificity() throws InterruptedException {
        CountDownLatch testLatch = new CountDownLatch(1);
        CountDownLatch otherLatch = new CountDownLatch(1);
        AtomicInteger testCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        eventBus.subscribe(TestEvent.class, event -> {
            testCount.incrementAndGet();
            testLatch.countDown();
        });

        eventBus.subscribe(OtherEvent.class, event -> {
            otherCount.incrementAndGet();
            otherLatch.countDown();
        });

        eventBus.publish(new TestEvent("Test"));

        boolean testReceived = testLatch.await(1, TimeUnit.SECONDS);
        assertTrue(testReceived);
        assertEquals(1, testCount.get());
        assertEquals(0, otherCount.get());
    }

    @Test
    void testShutdown() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        eventBus.subscribe(TestEvent.class, event -> {
            messageCount.incrementAndGet();
            latch.countDown();
        });

        eventBus.shutdown();

        // Try to publish after shutdown (might be processed or not, but shouldn't crash)
        eventBus.publish(new TestEvent("Test"));

        boolean noMessageReceived = latch.await(100, TimeUnit.MILLISECONDS);
        assertFalse(noMessageReceived);
    }

    static class TestEvent extends DomainEvent {
        private final String message;

        public TestEvent(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    static class OtherEvent extends DomainEvent {
        private final int value;

        public OtherEvent(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
