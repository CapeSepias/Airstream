package com.raquo.airstream.combine

import com.raquo.airstream.common.{InternalParentObserver, MultiParentStream, Observation}
import com.raquo.airstream.core.{EventStream, Observable, Protected, SyncObservable, Transaction, WritableStream}
import com.raquo.airstream.util.JsPriorityQueue
import com.raquo.ew.JsArray

/** Stream that emit events from all of its parents.
  *
  * Note: this stream re-emits errors emitted by all of its parents
  *
  * This feature exists only for EventStream-s because merging Signals
  * does not make sense, conceptually (what do you even do with their current values?).
  *
  * @param parentStreams Never update this array - this stream owns it.
  */
class MergeStream[A](
  parentStreams: JsArray[EventStream[A]],
) extends WritableStream[A] with SyncObservable[A] with MultiParentStream[A, A] {

  override protected[this] val parents: JsArray[Observable[A]] = {
    // This cast is safe as long as we don't put signals into this array
    parentStreams.asInstanceOf[JsArray[Observable[A]]]
  }

  override protected val topoRank: Int = Protected.maxTopoRank(parents) + 1

  private[this] val pendingParentValues: JsPriorityQueue[Observation[A]] = {
    new JsPriorityQueue(observation => Protected.topoRank(observation.observable))
  }

  private[this] val parentObservers: JsArray[InternalParentObserver[A]] = JsArray()

  parents.forEach(parent => parentObservers.push(makeInternalObserver(parent)))

  // @TODO document this, and document the topo parent order
  /** If this stream has already fired in a given transaction, the next firing will happen in a new transaction.
    *
    * This is needed for a combination of two reasons:
    * 1) only one event can propagate in a transaction at the same time
    * 2) We do not want the merged stream to "swallow" events
    *
    * We made it this way because the user probably expects this behavior.
    */
  override private[airstream] def syncFire(transaction: Transaction): Unit = {
    // @TODO[Integrity] I don't think we actually need this "check":
    // At least one value is guaranteed to exist if this observable is pending
    // pendingParentValues.dequeue().value.fold(fireError(_, transaction), fireValue(_, transaction))

    while (pendingParentValues.nonEmpty) {
      pendingParentValues.dequeue().value.fold(
        fireError(_, transaction),
        fireValue(_, transaction)
      )
    }
  }

  override protected[this] def onStart(): Unit = {
    parentObservers.forEach(_.addToParent(shouldCallMaybeWillStart = false))
    super.onStart()
  }

  override protected[this] def onStop(): Unit = {
    parentObservers.forEach(_.removeFromParent())
    super.onStop()
  }

  private def makeInternalObserver(parent: Observable[A]): InternalParentObserver[A] = {
    InternalParentObserver.fromTry(parent, (nextValue, transaction) => {
      pendingParentValues.enqueue(new Observation(parent, nextValue))
      // @TODO[API] Actually, why are we checking for .contains here? We need to better define behaviour
      // @TODO Make a test case that would exercise this .contains check or lack thereof
      // @TODO I think this check is moot because we can't have an observable emitting more than once in a transaction. Or can we? I feel like we can't/ It should probably be part of the transaction contract.
      if (!transaction.pendingObservables.contains(this)) {
        transaction.pendingObservables.enqueue(this)
      }
    })
  }
}
