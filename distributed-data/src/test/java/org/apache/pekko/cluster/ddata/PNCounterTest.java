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

package org.apache.pekko.cluster.ddata;

import java.math.BigInteger;

public class PNCounterTest {

  public void compileOnlyPNCounterApiTest() {
    // primarily to check API accessibility with overloads/types
    SelfUniqueAddress node1 = null;
    SelfUniqueAddress node2 = null;

    PNCounter c1 = PNCounter.create();

    PNCounter c2 = c1.increment(node1, BigInteger.valueOf(3));
    PNCounter c3 = c2.increment(node1, 4L);

    PNCounter c4 = c3.decrement(node2, BigInteger.valueOf(2));
    PNCounter c5 = c4.decrement(node2, 7L);
  }
}
