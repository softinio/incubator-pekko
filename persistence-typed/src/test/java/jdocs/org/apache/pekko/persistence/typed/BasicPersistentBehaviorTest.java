/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.persistence.typed;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.DeleteEventsFailed;
import org.apache.pekko.persistence.typed.DeleteSnapshotsFailed;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.SnapshotFailed;
import org.apache.pekko.persistence.typed.SnapshotSelectionCriteria;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.Recovery;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
// #behavior
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.persistence.typed.PersistenceId;

// #behavior
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.apache.pekko.persistence.typed.javadsl.SignalHandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BasicPersistentBehaviorTest {

  interface Structure {
    // #structure
    public class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {

      interface Command {}

      interface Event {}

      public static class State {}

      public static Behavior<Command> create(PersistenceId persistenceId) {
        return new MyPersistentBehavior(persistenceId);
      }

      private MyPersistentBehavior(PersistenceId persistenceId) {
        super(persistenceId);
      }

      @Override
      public State emptyState() {
        return new State();
      }

      @Override
      public CommandHandler<Command, Event, State> commandHandler() {
        return (state, command) -> {
          throw new RuntimeException("TODO: process the command & return an Effect");
        };
      }

      @Override
      public EventHandler<State, Event> eventHandler() {
        return (state, event) -> {
          throw new RuntimeException("TODO: process the event return the next state");
        };
      }
    }
    // #structure
  }

  interface FirstExample {
    // #behavior
    public class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {

      // #behavior

      // #command
      interface Command {}

      public static class Add implements Command {
        public final String data;

        public Add(String data) {
          this.data = data;
        }
      }

      public enum Clear implements Command {
        INSTANCE
      }

      interface Event {}

      public static class Added implements Event {
        public final String data;

        public Added(String data) {
          this.data = data;
        }
      }

      public enum Cleared implements Event {
        INSTANCE
      }
      // #command

      // #state
      public static class State {
        private final List<String> items;

        private State(List<String> items) {
          this.items = items;
        }

        public State() {
          this.items = new ArrayList<>();
        }

        public State addItem(String data) {
          List<String> newItems = new ArrayList<>(items);
          newItems.add(0, data);
          // keep 5 items
          List<String> latest = newItems.subList(0, Math.min(5, newItems.size()));
          return new State(latest);
        }
      }
      // #state

      // #behavior
      // commands, events and state defined here

      public static Behavior<Command> create(PersistenceId persistenceId) {
        return new MyPersistentBehavior(persistenceId);
      }

      private MyPersistentBehavior(PersistenceId persistenceId) {
        super(persistenceId);
      }

      @Override
      public State emptyState() {
        return new State();
      }

      // #command-handler
      @Override
      public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Add.class, command -> Effect().persist(new Added(command.data)))
            .onCommand(Clear.class, command -> Effect().persist(Cleared.INSTANCE))
            .build();
      }
      // #command-handler

      // #event-handler
      @Override
      public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Added.class, (state, event) -> state.addItem(event.data))
            .onEvent(Cleared.class, () -> new State())
            .build();
      }
      // #event-handler
    }
    // #behavior

  }

  interface Effects {
    public class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {

      interface Command {}

      public static class Add implements Command {
        public final String data;

        public Add(String data) {
          this.data = data;
        }
      }

      public enum Clear implements Command {
        INSTANCE
      }

      interface Event {}

      public static class Added implements Event {
        public final String data;

        public Added(String data) {
          this.data = data;
        }
      }

      public enum Cleared implements Event {
        INSTANCE
      }

      public static class State {
        private final List<String> items;

        private State(List<String> items) {
          this.items = items;
        }

        public State() {
          this.items = new ArrayList<>();
        }

        public State addItem(String data) {
          List<String> newItems = new ArrayList<>(items);
          newItems.add(0, data);
          // keep 5 items
          List<String> latest = newItems.subList(0, Math.min(4, newItems.size() - 1));
          return new State(latest);
        }
      }

      public static Behavior<Command> create(
          PersistenceId persistenceId, ActorRef<State> subscriber) {
        return new MyPersistentBehavior(persistenceId, subscriber);
      }

      private MyPersistentBehavior(PersistenceId persistenceId, ActorRef<State> subscriber) {
        super(persistenceId);
        this.subscriber = subscriber;
      }

      @Override
      public State emptyState() {
        return new State();
      }

      // #effects
      private final ActorRef<State> subscriber;

      @Override
      public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Add.class, this::onAdd)
            .onCommand(Clear.class, this::onClear)
            .build();
      }

      private Effect<Event, State> onAdd(Add command) {
        return Effect()
            .persist(new Added(command.data))
            .thenRun(newState -> subscriber.tell(newState));
      }

      private Effect<Event, State> onClear(Clear command) {
        return Effect()
            .persist(Cleared.INSTANCE)
            .thenRun(newState -> subscriber.tell(newState))
            .thenStop();
      }

      // #effects

      @Override
      public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Added.class, (state, event) -> state.addItem(event.data))
            .onEvent(Cleared.class, () -> new State())
            .build();
      }
    }
  }

  interface More {

    // #supervision
    public class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {

      // #supervision
      interface Command {}

      interface Event {}

      public static class State {}
      // #supervision

      public static Behavior<Command> create(PersistenceId persistenceId) {
        return new MyPersistentBehavior(persistenceId);
      }

      private MyPersistentBehavior(PersistenceId persistenceId) {
        super(
            persistenceId,
            SupervisorStrategy.restartWithBackoff(
                Duration.ofSeconds(10), Duration.ofSeconds(30), 0.2));
      }

      // #supervision

      @Override
      public State emptyState() {
        return new State();
      }

      @Override
      public CommandHandler<Command, Event, State> commandHandler() {
        return (state, command) -> {
          throw new RuntimeException("TODO: process the command & return an Effect");
        };
      }

      @Override
      public EventHandler<State, Event> eventHandler() {
        return (state, event) -> {
          throw new RuntimeException("TODO: process the event return the next state");
        };
      }

      // #recovery

      @Override
      public SignalHandler<State> signalHandler() {
        return newSignalHandlerBuilder()
            .onSignal(
                RecoveryCompleted.instance(),
                state -> {
                  throw new RuntimeException("TODO: add some end-of-recovery side-effect here");
                })
            .build();
      }
      // #recovery

      // #recovery-disabled
      @Override
      public Recovery recovery() {
        return Recovery.disabled();
      }
      // #recovery-disabled

      // #tagging
      @Override
      public Set<String> tagsFor(Event event) {
        Set<String> tags = new HashSet<>();
        tags.add("tag1");
        tags.add("tag2");
        return tags;
      }
      // #tagging
      // #supervision
    }
    // #supervision
  }

  interface More2 {

    // #wrapPersistentBehavior
    public class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {

      // #wrapPersistentBehavior
      interface Command {}

      interface Event {}

      public static class State {}
      // #wrapPersistentBehavior

      public static Behavior<Command> create(PersistenceId persistenceId) {
        return Behaviors.setup(context -> new MyPersistentBehavior(persistenceId, context));
      }

      private final ActorContext<Command> context;

      private MyPersistentBehavior(PersistenceId persistenceId, ActorContext<Command> context) {
        super(
            persistenceId,
            SupervisorStrategy.restartWithBackoff(
                Duration.ofSeconds(10), Duration.ofSeconds(30), 0.2));
        this.context = context;
      }

      // #wrapPersistentBehavior

      @Override
      public State emptyState() {
        return new State();
      }

      @Override
      public CommandHandler<Command, Event, State> commandHandler() {
        return (state, command) -> {
          throw new RuntimeException("TODO: process the command & return an Effect");
        };
      }

      @Override
      public EventHandler<State, Event> eventHandler() {
        return (state, event) -> {
          throw new RuntimeException("TODO: process the event return the next state");
        };
      }

      // #wrapPersistentBehavior
      @Override
      public boolean shouldSnapshot(State state, Event event, long sequenceNr) {
        context
            .getLog()
            .info("Snapshot actor {} => state: {}", context.getSelf().path().name(), state);
        return true;
      }
    }
    // #wrapPersistentBehavior
  }

  interface TaggingQuery {

    public abstract class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {

      interface Command {}

      interface Event {}

      interface OrderCompleted extends Event {}

      public static class State {}

      MyPersistentBehavior(String entityId) {
        super(PersistenceId.of("ShoppingCart", entityId));
        this.entityId = entityId;
      }

      // #tagging-query
      private final String entityId;

      public static final int NUMBER_OF_ENTITY_GROUPS = 10;

      @Override
      public Set<String> tagsFor(Event event) {
        String entityGroup = "group-" + Math.abs(entityId.hashCode() % NUMBER_OF_ENTITY_GROUPS);
        Set<String> tags = new HashSet<>();
        tags.add(entityGroup);
        if (event instanceof OrderCompleted) tags.add("order-completed");
        return tags;
      }
      // #tagging-query
    }
  }

  interface Snapshotting {

    public class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {

      interface Command {}

      interface Event {}

      public static class BookingCompleted implements Event {}

      public static class State {}

      public static Behavior<Command> create(PersistenceId persistenceId) {
        return new MyPersistentBehavior(persistenceId);
      }

      private MyPersistentBehavior(PersistenceId persistenceId) {
        super(persistenceId);
      }

      @Override
      public State emptyState() {
        return new State();
      }

      @Override
      public CommandHandler<Command, Event, State> commandHandler() {
        return (state, command) -> {
          throw new RuntimeException("TODO: process the command & return an Effect");
        };
      }

      @Override
      public EventHandler<State, Event> eventHandler() {
        return (state, event) -> {
          throw new RuntimeException("TODO: process the event return the next state");
        };
      }

      // #snapshottingPredicate
      @Override // override shouldSnapshot in EventSourcedBehavior
      public boolean shouldSnapshot(State state, Event event, long sequenceNr) {
        return event instanceof BookingCompleted;
      }
      // #snapshottingPredicate

      // #retentionCriteria
      @Override // override retentionCriteria in EventSourcedBehavior
      public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(100, 2);
      }
      // #retentionCriteria

      // #retentionCriteriaWithSignals
      @Override
      public SignalHandler<State> signalHandler() {
        return newSignalHandlerBuilder()
            .onSignal(
                SnapshotFailed.class,
                (state, completed) -> {
                  throw new RuntimeException("TODO: add some on-snapshot-failed side-effect here");
                })
            .onSignal(
                DeleteSnapshotsFailed.class,
                (state, completed) -> {
                  throw new RuntimeException(
                      "TODO: add some on-delete-snapshot-failed side-effect here");
                })
            .onSignal(
                DeleteEventsFailed.class,
                (state, completed) -> {
                  throw new RuntimeException(
                      "TODO: add some on-delete-snapshot-failed side-effect here");
                })
            .build();
      }
      // #retentionCriteriaWithSignals
    }
  }

  public static class Snapshotting2 extends Snapshotting.MyPersistentBehavior {
    public Snapshotting2(PersistenceId persistenceId) {
      super(persistenceId);
    }

    // #snapshotAndEventDeletes
    @Override // override retentionCriteria in EventSourcedBehavior
    public RetentionCriteria retentionCriteria() {
      return RetentionCriteria.snapshotEvery(100, 2).withDeleteEventsOnSnapshot();
    }
    // #snapshotAndEventDeletes
  }

  public static class SnapshotSelection extends Snapshotting.MyPersistentBehavior {
    public SnapshotSelection(PersistenceId persistenceId) {
      super(persistenceId);
    }

    // #snapshotSelection
    @Override
    public Recovery recovery() {
      return Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none());
    }
    // #snapshotSelection
  }

  interface WithActorContext {

    // #actor-context
    public class MyPersistentBehavior
        extends EventSourcedBehavior<
            MyPersistentBehavior.Command, MyPersistentBehavior.Event, MyPersistentBehavior.State> {
      // #actor-context

      interface Command {}

      interface Event {}

      public static class State {}
      // #actor-context

      public static Behavior<Command> create(PersistenceId persistenceId) {
        return Behaviors.setup(ctx -> new MyPersistentBehavior(persistenceId, ctx));
      }

      // this makes the context available to the command handler etc.
      private final ActorContext<Command> context;

      // optionally if you only need `ActorContext.getSelf()`
      private final ActorRef<Command> self;

      public MyPersistentBehavior(PersistenceId persistenceId, ActorContext<Command> ctx) {
        super(persistenceId);
        this.context = ctx;
        this.self = ctx.getSelf();
      }

      // #actor-context
      @Override
      public State emptyState() {
        return null;
      }

      @Override
      public CommandHandler<Command, Event, State> commandHandler() {
        return null;
      }

      @Override
      public EventHandler<State, Event> eventHandler() {
        return null;
      }
      // #actor-context
    }
    // #actor-context
  }
}
