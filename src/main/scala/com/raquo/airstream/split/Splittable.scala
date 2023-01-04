package com.raquo.airstream.split

import scala.collection.immutable
import scala.scalajs.js

/** The `split` operator needs an implicit instance of Splittable[M] in order to work on observables of M[_] */
trait Splittable[M[_]] {

  def map[A, B](inputs: M[A], project: A => B): M[B]

  def empty[A]: M[A]
}

object Splittable extends LowPrioritySplittableImplicits {

  implicit object SeqSplittable extends Splittable[collection.Seq] {

    override def map[A, B](inputs: collection.Seq[A], project: A => B): collection.Seq[B] = inputs.map(project)

    override def empty[A]: collection.Seq[A] = Nil
  }

  implicit object OptionSplittable extends Splittable[Option] {

    override def map[A, B](inputs: Option[A], project: A => B): Option[B] = inputs.map(project)

    override def empty[A]: Option[A] = None
  }

  implicit object JsArraySplittable extends Splittable[js.Array] {

    override def map[A, B](inputs: js.Array[A], project: A => B): js.Array[B] = inputs.map(project)

    override def empty[A]: js.Array[A] = js.Array()
  }
}

/** Scala 3.0.0 is smart enough to not need these implicits. SOMETIMES.
  * Other times, it needs them, depending on random crap like whether
  * you provided a `distinctCompose` argument to `split` or not.
  *
  * Thus, we need to store these implicits in a separate trait
  * to avoid ambiguous-implicit errors.
  */
trait LowPrioritySplittableImplicits {

  implicit object ListSplittable extends Splittable[List] {

    override def map[A, B](inputs: List[A], project: A => B): List[B] = inputs.map(project)

    override def empty[A]: List[A] = Nil
  }

  implicit object VectorSplittable extends Splittable[Vector] {

    override def map[A, B](inputs: Vector[A], project: A => B): Vector[B] = inputs.map(project)

    override def empty[A]: Vector[A] = Vector.empty
  }

  implicit object SetSplittable extends Splittable[Set] {

    override def map[A, B](inputs: Set[A], project: A => B): Set[B] = inputs.map(project)

    override def empty[A]: Set[A] = Set.empty
  }

  implicit object ImmutableSeqSplittable extends Splittable[immutable.Seq] {

    override def map[A, B](inputs: immutable.Seq[A], project: A => B): immutable.Seq[B] = inputs.map(project)

    override def empty[A]: immutable.Seq[A] = Nil
  }
}
