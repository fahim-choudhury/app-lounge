package app.lounge.storage.cache

import android.content.Context
import kotlin.reflect.KProperty


//region Setter Accessors (primarily used for setting)

val Context.configurations: PersistedConfiguration get() = PersistedConfiguration(context = this)

//endregion

// region - Persistence Configuration

internal enum class PersistenceKey {
    updateInstallAuto,
    updateCheckIntervals,
    updateAppsFromOtherStores,
    showAllApplications,
    showPWAApplications,
    showFOSSApplications,
    // OLD datastore
    authData,
    email,
    oauthtoken,
    userType,
    tocStatus,
    tosversion
}

class PersistedConfiguration(context: Context) {
    var updateInstallAuto by context.persisted(PersistenceKey.updateInstallAuto, false)
    var updateCheckIntervals by context.persisted(PersistenceKey.updateCheckIntervals, 24)
    var updateAppsFromOtherStores by context.persisted(PersistenceKey.updateAppsFromOtherStores, false)
    var showAllApplications by context.persisted(PersistenceKey.showAllApplications, true)
    var showPWAApplications by context.persisted(PersistenceKey.showPWAApplications, true)
    var showFOSSApplications by context.persisted(PersistenceKey.showFOSSApplications, true)
    var authData by context.persisted(PersistenceKey.authData, "")
    var email by context.persisted(PersistenceKey.email, "")
    var oauthtoken by context.persisted(PersistenceKey.oauthtoken, "")
    var userType by context.persisted(PersistenceKey.userType, "")
    var tocStatus by context.persisted(PersistenceKey.tocStatus, false)
    var tosversion by context.persisted(PersistenceKey.tosversion, "")
}

// endregion

//region - Persistence (in shared preferences)

private class PersistedItem<T>(
    context: Context,
    val key: PersistenceKey,
    var defaultValue: T
) {

    private val sharedPref =
        context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
    private val sharedPrefKey = "${context.packageName}." + key.name


    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return try {
            when (property.returnType.classifier) {
                Int::class -> sharedPref.getInt(sharedPrefKey, defaultValue as Int)
                Long::class -> sharedPref.getLong(sharedPrefKey, defaultValue as Long)
                Boolean::class -> sharedPref.getBoolean(sharedPrefKey, defaultValue as Boolean)
                String::class -> sharedPref.getString(sharedPrefKey, defaultValue as String)
                else -> IllegalArgumentException(
                    "TODO: Missing accessor for type -- ${property.returnType.classifier}"
                )
            } as T
        } catch (e: ClassCastException) {
            defaultValue
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        sharedPref.edit().apply {
            when (value) {
                is Int -> putInt(sharedPrefKey, value)
                is Long -> putLong(sharedPrefKey, value)
                is Boolean -> putBoolean(sharedPrefKey, value)
                is String -> putString(sharedPrefKey, value)
                else -> IllegalArgumentException(
                    "TODO: Missing setter for type -- ${property.returnType.classifier}"
                )
            }
            apply()
        }
    }
}


private fun <T> Context.persisted(key: PersistenceKey, defaultValue: T) : PersistedItem<T> {
    return PersistedItem(context = this, key = key, defaultValue = defaultValue)
}

//endregion