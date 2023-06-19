package app.lounge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lounge.storage.cache.PersistedConfiguration
import app.lounge.storage.cache.PersistenceKey
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.KClassifier

@RunWith(AndroidJUnit4::class)
class PersistentStorageTest {

    private lateinit var testConfiguration: PersistedConfiguration

    @Before
    fun setupPersistentConfiguration(){
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        testConfiguration = PersistedConfiguration(appContext)
    }

    @Test
    fun testOnMethodInvokeReturnCorrectType(){
        PersistenceKey.values().toList().forEach { persistenceKey ->

            val propertyReturnType = testConfiguration.getPropertyReturnType(persistenceKey.name)
            val propertyValue = testConfiguration.callMethod(persistenceKey.name)

            when(propertyReturnType){
                Int::class -> Assert.assertNotEquals(propertyValue, 0)
                String::class -> Assert.assertNotEquals(propertyValue, null)
                Boolean::class -> Assert.assertNotEquals(propertyValue, null)
            }
        }
    }
}

// Utils function for `Persistence` Testcase only
private inline fun <reified T> T.callMethod(name: String, vararg args: Any?): Any? =
    T::class
        .members
        .firstOrNull { it.name == name }
        ?.call(this, *args)

private inline fun <reified T : Any> T.getPropertyReturnType(name: String): KClassifier? =
    T::class
        .members
        .firstOrNull { it.name == name }
        ?.returnType
        ?.classifier