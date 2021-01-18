package com.raquo.airstream.debug

import com.raquo.airstream.common.{ InternalTryObserver, SingleParentObservable }
import com.raquo.airstream.core.{ AirstreamError, Signal, Transaction, WritableSignal }

import scala.util.Try

/** This signal helps you debug when parent stream is started / stopped.
  *
  * Use this signal as a replacement for the parent signal for it to work.
  *
  * Note: exceptions in provided callbacks will be sent directly to unhandled errors
  */
class DebugLifecycleSignal[A](
  override protected val parent: Signal[A],
  start: () => Unit,
  stop: () => Unit,
  initial: Try[A] => Unit
) extends Signal[A] with WritableSignal[A] with SingleParentObservable[A, A] with InternalTryObserver[A] {

  override protected[airstream] val topoRank: Int = parent.topoRank + 1

  override protected def initialValue: Try[A] = {
    val initValue = parent.tryNow()
    Try(initial(initValue)).recover { case err => AirstreamError.sendUnhandledError(err) }
    initValue
  }

  override protected[airstream] def onTry(nextValue: Try[A], transaction: Transaction): Unit = {
    fireTry(nextValue, transaction)
  }

  override protected def onStart(): Unit = {
    super.onStart()
    Try(start()).recover { case err => AirstreamError.sendUnhandledError(err) }
  }

  override protected def onStop(): Unit = {
    super.onStop()
    Try(stop()).recover { case err => AirstreamError.sendUnhandledError(err) }
  }
}