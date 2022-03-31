package io.github.karino2.pngnote.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefManager @Inject constructor(@ApplicationContext context : Context){

    val preferences: SharedPreferences = getDefaultSharedPreferences(context)

    private var uri : String?
        get() = preferences.getString("last_root_url", null)
        set(value) {preferences.edit().putString("last_root_url", value).apply()}

    fun setUri(value: Uri) { uri = value.toString() }
    fun getUri() = uri?.let { Uri.parse(it) }

    companion object {
        private const val NAME = "KAKIOKU"
        private const val MODE = Context.MODE_PRIVATE
    }
}