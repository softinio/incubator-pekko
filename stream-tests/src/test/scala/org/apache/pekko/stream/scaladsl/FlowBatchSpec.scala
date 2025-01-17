/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream.OverflowStrategy
import pekko.stream.testkit._

class FlowBatchSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
    pekko.stream.materializer.max-input-buffer-size = 2
  """) {

  "Batch" must {

    "pass-through elements unchanged when there is no rate difference" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batch(max = 2, seed = i => i)(aggregate = _ + _)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      for (i <- 1 to 100) {
        sub.request(1)
        publisher.sendNext(i)
        subscriber.expectNext(i)
      }

      sub.cancel()
    }

    "aggregate elements while downstream is silent" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[List[Int]]()

      Source
        .fromPublisher(publisher)
        .batch(max = Long.MaxValue, seed = i => List(i))(aggregate = (ints, i) => i :: ints)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      for (i <- 1 to 10) {
        publisher.sendNext(i)
      }
      subscriber.expectNoMessage(1.second)
      sub.request(1)
      subscriber.expectNext(List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1))
      sub.cancel()
    }

    "work on a variable rate chain" in {
      val future = Source(1 to 1000)
        .batch(max = 100, seed = i => i)(aggregate = (sum, i) => sum + i)
        .map { i =>
          if (ThreadLocalRandom.current().nextBoolean()) Thread.sleep(10); i
        }
        .runFold(0)(_ + _)
      Await.result(future, 10.seconds) should be(500500)
    }

    "backpressure subscriber when upstream is slower" in {
      val publisher = TestPublisher.probe[Int]()
      val subscriber = TestSubscriber.manualProbe[Int]()

      Source
        .fromPublisher(publisher)
        .batch(max = 2, seed = i => i)(aggregate = _ + _)
        .to(Sink.fromSubscriber(subscriber))
        .run()
      val sub = subscriber.expectSubscription()

      sub.request(1)
      publisher.sendNext(1)
      subscriber.expectNext(1)

      sub.request(1)
      subscriber.expectNoMessage(500.millis)
      publisher.sendNext(2)
      subscriber.expectNext(2)

      publisher.sendNext(3)
      publisher.sendNext(4)
      // The request can be in race with the above onNext(4) so the result would be either 3 or 7.
      subscriber.expectNoMessage(500.millis)
      sub.request(1)
      subscriber.expectNext(7)

      sub.request(1)
      subscriber.expectNoMessage(500.millis)
      sub.cancel()

    }

    "work with a buffer and fold" in {
      val future = Source(1 to 50)
        .batch(max = Long.MaxValue, seed = i => i)(aggregate = _ + _)
        .buffer(50, OverflowStrategy.backpressure)
        .runFold(0)(_ + _)
      Await.result(future, 3.seconds) should be((1 to 50).sum)
    }

  }
}
