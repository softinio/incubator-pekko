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

package org.apache.pekko.actor

import scala.collection.immutable

import scala.annotation.nowarn

import org.apache.pekko
import pekko.japi.Creator
import pekko.util.Reflect

/**
 * This interface defines a class of actor creation strategies deviating from
 * the usual default of just reflectively instantiating the [[Actor]]
 * subclass. It can be used to allow a dependency injection framework to
 * determine the actual actor class and how it shall be instantiated.
 */
trait IndirectActorProducer {

  /**
   * This factory method must produce a fresh actor instance upon each
   * invocation. <b>It is not permitted to return the same instance more than
   * once.</b>
   */
  def produce(): Actor

  /**
   * This method is used by [[Props]] to determine the type of actor which will
   * be created. This means that an instance of this `IndirectActorProducer`
   * will be created in order to call this method during any call to
   * [[Props#actorClass]]; it should be noted that such calls may
   * performed during actor set-up before the actual actor’s instantiation, and
   * that the instance created for calling `actorClass` is not necessarily reused
   * later to produce the actor.
   */
  def actorClass: Class[_ <: Actor]
}

private[pekko] object IndirectActorProducer {
  val CreatorFunctionConsumerClass = classOf[CreatorFunctionConsumer]
  val CreatorConsumerClass = classOf[CreatorConsumer]
  val TypedCreatorFunctionConsumerClass = classOf[TypedCreatorFunctionConsumer]
  @nowarn
  def apply(clazz: Class[_], args: immutable.Seq[Any]): IndirectActorProducer = {
    if (classOf[IndirectActorProducer].isAssignableFrom(clazz)) {
      def get1stArg[T]: T = args.head.asInstanceOf[T]
      def get2ndArg[T]: T = args.tail.head.asInstanceOf[T]
      // The cost of doing reflection to create these for every props
      // is rather high, so we match on them and do new instead
      clazz match {
        case TypedCreatorFunctionConsumerClass =>
          new TypedCreatorFunctionConsumer(get1stArg, get2ndArg)
        case CreatorFunctionConsumerClass =>
          new CreatorFunctionConsumer(get1stArg)
        case CreatorConsumerClass =>
          new CreatorConsumer(get1stArg, get2ndArg)
        case _ =>
          Reflect.instantiate(clazz, args).asInstanceOf[IndirectActorProducer]
      }
    } else if (classOf[Actor].isAssignableFrom(clazz)) {
      if (args.isEmpty) new NoArgsReflectConstructor(clazz.asInstanceOf[Class[_ <: Actor]])
      else new ArgsReflectConstructor(clazz.asInstanceOf[Class[_ <: Actor]], args)
    } else throw new IllegalArgumentException(s"unknown actor creator [$clazz]")
  }
}

/**
 * INTERNAL API
 */
private[pekko] class CreatorFunctionConsumer(creator: () => Actor) extends IndirectActorProducer {
  override def actorClass = classOf[Actor]
  override def produce() = creator()
}

/**
 * INTERNAL API
 */
private[pekko] class CreatorConsumer(clazz: Class[_ <: Actor], creator: Creator[Actor]) extends IndirectActorProducer {
  override def actorClass = clazz
  override def produce() = creator.create()
}

/**
 * INTERNAL API
 */
private[pekko] class TypedCreatorFunctionConsumer(clz: Class[_ <: Actor], creator: () => Actor)
    extends IndirectActorProducer {
  override def actorClass = clz
  override def produce() = creator()
}

/**
 * INTERNAL API
 */
private[pekko] class ArgsReflectConstructor(clz: Class[_ <: Actor], args: immutable.Seq[Any])
    extends IndirectActorProducer {
  private[this] val constructor = Reflect.findConstructor(clz, args)
  override def actorClass = clz
  override def produce() = Reflect.instantiate(constructor, args)
}

/**
 * INTERNAL API
 */
private[pekko] class NoArgsReflectConstructor(clz: Class[_ <: Actor]) extends IndirectActorProducer {
  Reflect.findConstructor(clz, List.empty)
  override def actorClass = clz
  override def produce() = Reflect.instantiate(clz)
}
