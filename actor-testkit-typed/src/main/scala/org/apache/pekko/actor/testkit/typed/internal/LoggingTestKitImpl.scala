/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed.internal

import java.util.function.Supplier

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.matching.Regex

import org.slf4j.event.Level

import org.apache.pekko
import pekko.actor.testkit.typed.LoggingEvent
import pekko.actor.testkit.typed.TestKitSettings
import pekko.actor.testkit.typed.javadsl
import pekko.actor.testkit.typed.scaladsl
import pekko.actor.typed.ActorSystem
import pekko.annotation.InternalApi
import pekko.testkit.TestKit

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object LoggingTestKitImpl {
  def empty: LoggingTestKitImpl =
    new LoggingTestKitImpl(1, None, None, None, None, None, None, Map.empty, checkExcess = true, None)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class LoggingTestKitImpl(
    occurrences: Int,
    logLevel: Option[Level],
    loggerName: Option[String],
    source: Option[String],
    messageContains: Option[String],
    messageRegex: Option[Regex],
    cause: Option[Class[_ <: Throwable]],
    mdc: Map[String, String],
    checkExcess: Boolean,
    custom: Option[Function[LoggingEvent, Boolean]])
    extends javadsl.LoggingTestKit
    with scaladsl.LoggingTestKit {

  @volatile // JMM does not guarantee visibility for non-final fields
  private var todo = occurrences

  def matches(event: LoggingEvent): Boolean = {
    logLevel.forall(_ == event.level) &&
    source.forall(_ == sourceOrEmpty(event)) &&
    messageContains.forall(messageOrEmpty(event).contains) &&
    messageRegex.forall(_.findFirstIn(messageOrEmpty(event)).isDefined) &&
    cause.forall(c => event.throwable.isDefined && c.isInstance(event.throwable.get)) &&
    mdc.forall { case (key, value) => event.mdc.contains(key) && event.mdc(key) == value } &&
    custom.forall(f => f(event))

    // loggerName is handled when installing the filter, in `expect`
  }

  private def messageOrEmpty(event: LoggingEvent): String =
    if (event.message == null) "" else event.message

  private def sourceOrEmpty(event: LoggingEvent): String =
    event.mdc.getOrElse("pekkoSource", "")

  def apply(event: LoggingEvent): Boolean = {
    if (matches(event)) {
      if (todo != Int.MaxValue) todo -= 1
      true
    } else false
  }

  private def awaitDone(max: Duration): Boolean = {
    if (todo != Int.MaxValue && todo > 0) TestKit.awaitCond(todo <= 0, max, noThrow = true)
    todo == Int.MaxValue || todo == 0
  }

  private def awaitNoExcess(max: Duration): Boolean = {
    if (todo == 0)
      !TestKit.awaitCond(todo < 0, max, noThrow = true)
    else
      todo > 0
  }

  override def expect[T](code: => T)(implicit system: ActorSystem[_]): T = {
    val effectiveLoggerName = loggerName.getOrElse("")
    checkLogback(system)
    TestAppender.setupTestAppender(effectiveLoggerName)
    TestAppender.addFilter(effectiveLoggerName, this)
    val settings = TestKitSettings(system)
    try {
      val result = code

      // wait some more when occurrences=0 to find asynchronous excess messages
      if (occurrences == 0)
        awaitNoExcess(settings.ExpectNoMessageDefaultTimeout)

      if (!awaitDone(settings.FilterLeeway))
        if (todo > 0)
          throw new AssertionError(s"Timeout (${settings.FilterLeeway}) waiting for $todo messages on $this.")
        else if (checkExcess)
          throw new AssertionError(s"Received ${-todo} excess messages on $this.")
      result
    } finally {
      todo = occurrences
      TestAppender.removeFilter(effectiveLoggerName, this)
    }
  }

  override def expect[T](system: ActorSystem[_], code: Supplier[T]): T =
    expect(code.get())(system)

  // deprecated (renamed to expect)
  override def intercept[T](code: => T)(implicit system: ActorSystem[_]): T =
    expect(code)(system)

  private def checkLogback(system: ActorSystem[_]): Unit = {
    if (!system.dynamicAccess.classIsOnClasspath("ch.qos.logback.classic.spi.ILoggingEvent")) {
      throw new IllegalStateException("LoggingEventFilter requires logback-classic dependency in classpath.")
    }
  }

  override def withOccurrences(newOccurrences: Int): LoggingTestKitImpl =
    copy(occurrences = newOccurrences)

  override def withLogLevel(newLogLevel: Level): LoggingTestKitImpl =
    copy(logLevel = Option(newLogLevel))

  def withLoggerName(newLoggerName: String): LoggingTestKitImpl =
    copy(loggerName = Some(newLoggerName))

  override def withSource(newSource: String): LoggingTestKitImpl =
    copy(source = Option(newSource))

  override def withMessageContains(newMessageContains: String): LoggingTestKitImpl =
    copy(messageContains = Option(newMessageContains))

  def withMessageRegex(newMessageRegex: String): LoggingTestKitImpl =
    copy(messageRegex = Option(new Regex(newMessageRegex)))

  override def withCause[A <: Throwable: ClassTag]: LoggingTestKitImpl = {
    val causeClass = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[Throwable]]
    copy(cause = Option(causeClass))
  }

  override def withMdc(newMdc: Map[String, String]): LoggingTestKitImpl =
    copy(mdc = newMdc)

  override def withMdc(newMdc: java.util.Map[String, String]): javadsl.LoggingTestKit = {
    import pekko.util.ccompat.JavaConverters._
    withMdc(newMdc.asScala.toMap)
  }

  override def withCheckExcess(check: Boolean): LoggingTestKitImpl =
    copy(checkExcess = check)

  override def withCustom(newCustom: Function[LoggingEvent, Boolean]): LoggingTestKitImpl =
    copy(custom = Option(newCustom))

  override def withCause(newCause: Class[_ <: Throwable]): javadsl.LoggingTestKit =
    copy(cause = Option(newCause))

}
