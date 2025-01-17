/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor;

import org.apache.pekko.util.JavaDurationConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.FiniteDuration;

/**
 * An Apache Pekko scheduler service. This one needs one special behavior: if Closeable, it MUST
 * execute all outstanding tasks upon .close() in order to properly shutdown all dispatchers.
 *
 * <p>Furthermore, this timer service MUST throw IllegalStateException if it cannot schedule a task.
 * Once scheduled, the task MUST be executed. If executed upon close(), the task may execute before
 * its timeout.
 *
 * <p>Scheduler implementation are loaded reflectively at ActorSystem start-up with the following
 * constructor arguments: 1) the system’s com.typesafe.config.Config (from system.settings.config)
 * 2) a org.apache.pekko.event.LoggingAdapter 3) a java.util.concurrent.ThreadFactory
 */
public abstract class AbstractScheduler extends AbstractSchedulerBase {

  // FIXME #26910

  /**
   * Schedules a function to be run repeatedly with an initial delay and a frequency. E.g. if you
   * would like the function to be run after 2 seconds and thereafter every 100ms you would set
   * delay = Duration(2, TimeUnit.SECONDS) and interval = Duration(100, TimeUnit.MILLISECONDS)
   */
  @Deprecated
  @Override
  public abstract Cancellable schedule(
      FiniteDuration initialDelay,
      FiniteDuration interval,
      Runnable runnable,
      ExecutionContext executor);

  /**
   * Schedules a function to be run repeatedly with an initial delay and a frequency. E.g. if you
   * would like the function to be run after 2 seconds and thereafter every 100ms you would set
   * delay = Duration(2, TimeUnit.SECONDS) and interval = Duration.ofMillis(100)
   */
  @Deprecated
  public Cancellable schedule(
      final java.time.Duration initialDelay,
      final java.time.Duration interval,
      final Runnable runnable,
      final ExecutionContext executor) {
    return schedule(
        JavaDurationConverters.asFiniteDuration(initialDelay),
        JavaDurationConverters.asFiniteDuration(interval),
        runnable,
        executor);
  }

  /**
   * Schedules a Runnable to be run once with a delay, i.e. a time period that has to pass before
   * the runnable is executed.
   */
  @Override
  public abstract Cancellable scheduleOnce(
      FiniteDuration delay, Runnable runnable, ExecutionContext executor);

  /**
   * Schedules a Runnable to be run once with a delay, i.e. a time period that has to pass before
   * the runnable is executed.
   */
  public Cancellable scheduleOnce(
      final java.time.Duration delay, final Runnable runnable, final ExecutionContext executor) {
    return scheduleOnce(JavaDurationConverters.asFiniteDuration(delay), runnable, executor);
  }

  /**
   * The maximum supported task frequency of this scheduler, i.e. the inverse of the minimum time
   * interval between executions of a recurring task, in Hz.
   */
  @Override
  public abstract double maxFrequency();
}
