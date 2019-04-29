package foundation.e.apps.application.model

import foundation.e.apps.utils.Common
import foundation.e.apps.utils.Execute
import java.util.concurrent.atomic.AtomicBoolean

class ThreadedListeners(private val action: () -> Unit) {

    private val stopped = AtomicBoolean(false)
    private val waiter = Object()

    fun start() {
        Common.EXECUTOR.submit(this::run)
    }

    fun stop() {
        stopped.set(true)
        synchronized(waiter) {
            waiter.wait()
        }
    }

    private fun run() {
        while (!stopped.get()) {
            execSynchronized()
        }
    }

    private fun execSynchronized() {
        synchronized(waiter) {
            Execute({}, {
                action()
            })
            waiter.wait()
        }
    }

    private fun action() {
        action.invoke()
        synchronized(waiter) {
            waiter.notifyAll()
        }
    }
}
