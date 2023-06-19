package app.lounge

import app.lounge.storage.cache.PersistedConfiguration
import app.lounge.storage.cache.PersistenceKey
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        PersistenceKey.values().toList().forEach { persistenceKey ->
            println(persistenceKey)
        }
    }
}