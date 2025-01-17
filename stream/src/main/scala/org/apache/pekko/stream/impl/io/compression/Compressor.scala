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

package org.apache.pekko.stream.impl.io.compression

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ByteString

/**
 * INTERNAL API
 *
 * A stateful object representing ongoing compression.
 */
@InternalApi private[pekko] abstract class Compressor {

  /**
   * Compresses the given input and returns compressed data. The implementation
   * can and will choose to buffer output data to improve compression. Use
   * `flush` or `compressAndFlush` to make sure that all input data has been
   * compressed and pending output data has been returned.
   */
  def compress(input: ByteString): ByteString

  /**
   * Flushes any output data and returns the currently remaining compressed data.
   */
  def flush(): ByteString

  /**
   * Closes this compressed stream and return the remaining compressed data. After
   * calling this method, this Compressor cannot be used any further.
   */
  def finish(): ByteString

  /** Combines `compress` + `flush` */
  def compressAndFlush(input: ByteString): ByteString

  /** Combines `compress` + `finish` */
  def compressAndFinish(input: ByteString): ByteString

  /** Make sure any resources have been released */
  def close(): Unit
}
