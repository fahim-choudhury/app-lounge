package app.lounge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lounge.storage.cache.PersistedConfiguration
import app.lounge.storage.cache.PersistenceKey
import app.lounge.storage.cache.configurations
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
        testConfiguration = appContext.configurations
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

    @Test
    fun testOnSetPersistentKeyReturnsSameExpectedValue() {
        PersistenceKey.values().toList().forEach { persistentKey ->
            val returnType: KClassifier? = testConfiguration.getPropertyReturnType(persistentKey.name)
            when(persistentKey) {
                PersistenceKey.updateInstallAuto -> testConfiguration.updateInstallAuto = testBooleanValue
                PersistenceKey.updateCheckIntervals -> testConfiguration.updateCheckIntervals = testIntValue
                PersistenceKey.updateAppsFromOtherStores -> testConfiguration.updateAppsFromOtherStores = testBooleanValue
                PersistenceKey.showAllApplications -> testConfiguration.showAllApplications = testBooleanValue
                PersistenceKey.showPWAApplications -> testConfiguration.showPWAApplications = testBooleanValue
                PersistenceKey.showFOSSApplications -> testConfiguration.showFOSSApplications = testBooleanValue
                PersistenceKey.authData -> testConfiguration.authData = testStringValue
                PersistenceKey.email -> testConfiguration.email = testStringValue
                PersistenceKey.oauthtoken -> testConfiguration.oauthtoken = testStringValue
                PersistenceKey.userType -> testConfiguration.userType = testStringValue
                PersistenceKey.tocStatus -> testConfiguration.tocStatus = testBooleanValue
                PersistenceKey.tosversion -> testConfiguration.tosversion = testStringValue
            }
            testConfiguration.evaluateValue(classifier = returnType, key = persistentKey)
        }
    }
}

// Utils function for `Persistence` Testcase only
private val testIntValue : Int = (1..10).random()
private const val testStringValue: String = "quick brown fox jump over the lazy dog"
private const val testBooleanValue: Boolean = true

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
private fun PersistedConfiguration.evaluateValue(classifier: KClassifier?, key: PersistenceKey) {
    when(classifier){
        Int::class -> Assert.assertEquals(
            "Expected to be `$testIntValue`", testIntValue, this.callMethod(key.name) as Int)
        String::class -> Assert.assertEquals(
            "Expected to be `$testStringValue`", testStringValue, this.callMethod(key.name) as String)
        Boolean::class -> Assert.assertTrue(
            "Expected to be `$testBooleanValue`", this.callMethod(key.name) as Boolean)
    }
}