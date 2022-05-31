/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spring.scdf.customer;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * @author Corneil du Plessis
 */
@Component
@EnableBinding(Customer.Events.class)
public class Customer {
    private static final Logger logger = LoggerFactory.getLogger(Customer.class);
    private Random random = new Random(System.currentTimeMillis());
    private List<String> cold = Arrays.asList("water", "coke", "sprite");
    private List<String> hot = Arrays.asList("coffee", "tea");
    private List<String> food = Arrays.asList("burger", "pizza", "steak", "pasta");
    private final Events events;
    private final ApplicationAvailability applicationAvailability;

    private final Environment environment;
    private boolean placedOrder = false;

    public Customer(Events events, ApplicationAvailability applicationAvailability, Environment environment) {
        this.events = events;
        this.applicationAvailability = applicationAvailability;
        this.environment = environment;
    }

    public void placeOrders() {
        if (!placedOrder) {
            logger.info("placeOrder:start");
            placeOrder(cold);
            placeOrder(food);
            placeOrder(hot);
            placedOrder = true;
            logger.info("placeOrder:end");
        } else {
            logger.info("placeOrder:done");
        }
    }

    public void placeOrder(List<String> items) {
        String item = items.get(random.nextInt(items.size()));
        logger.info("Ordering:{}", item);
        events.order().send(MessageBuilder.withPayload(item).build());
    }


    @StreamListener(Events.RECEIVE)
    public void receive(String order) {
        logger.info("receive:{}", order);
        logger.info("pay for:{}", order);
        events.payment().send(MessageBuilder.withPayload("money for " + order).build());
    }

    @EventListener
    public void onEvent(AvailabilityChangeEvent<ReadinessState> event) {
        logger.info("onEvent:{}", event.getState());
        logger.info("availability:{}:{}", applicationAvailability.getLivenessState(), applicationAvailability.getReadinessState());
        if(!environment.acceptsProfiles(Profiles.of("test"))) {
            if (LivenessState.CORRECT.equals(applicationAvailability.getLivenessState())) {
                placeOrders();
            }
        } else {
            logger.info("onEvent:skip:test");
        }
    }

    public interface Events {
        String ORDER = "order";
        String RECEIVE = "receive";
        String PAYMENT = "payment";

        @Output(ORDER)
        MessageChannel order();

        @Output(PAYMENT)
        MessageChannel payment();

        @Input(RECEIVE)
        SubscribableChannel receive();
    }
}
