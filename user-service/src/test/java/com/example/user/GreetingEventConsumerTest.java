package com.example.user;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.shared.kafka.event.GreetingRequestedEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GreetingEventConsumerTest {

  private GreetingEventConsumer consumer;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    consumer = new GreetingEventConsumer();

    // Capture log output
    Logger logger = (Logger) LoggerFactory.getLogger(GreetingEventConsumer.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @Test
  void shouldLogReceivedEvent() {
    // given
    var event = new GreetingRequestedEvent("trace-123", 1L, "en", "Hello", Instant.now());

    // when
    consumer.handleGreetingRequested(event);

    // then
    assertThat(listAppender.list).isNotEmpty();
    String loggedMessage = listAppender.list.get(0).getFormattedMessage();
    assertThat(loggedMessage).contains("Received greeting event");
    assertThat(loggedMessage).contains("userId=1");
    assertThat(loggedMessage).contains("language=en");
    assertThat(loggedMessage).contains("greeting=Hello");
  }
}
