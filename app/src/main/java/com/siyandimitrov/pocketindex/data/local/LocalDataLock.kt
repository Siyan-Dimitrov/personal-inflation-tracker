package com.siyandimitrov.pocketindex.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * Serialises the multi-step writes that create local data against the destructive data reset.
 *
 * Capturing a receipt writes a private image, a receipt row, and an extraction job as separate
 * steps, and extraction creates a merchant in its own transaction before it saves line items.
 * Without this lock a reset can land between those steps and leave a receipt or an orphaned
 * merchant behind in a database the user was told is empty.
 */
@Singleton
class LocalDataLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.lock()
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
