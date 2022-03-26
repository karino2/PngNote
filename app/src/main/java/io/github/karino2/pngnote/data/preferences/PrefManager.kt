package io.github.karino2.pngnote.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

object PrefManager {

    private const val NAME = "KAKIOKU"
    private const val MODE = Context.MODE_PRIVATE
    private lateinit var preferences: SharedPreferences

    fun init(context: Context) { preferences = context.getSharedPreferences(NAME, MODE)}

    private var uri : String?
        get() = preferences.getString("last_root_url", null)
        set(value) {preferences.edit().putString("last_root_url", value).apply()}

    fun setUri(value: Uri) {uri = value.toString()}
    fun getUri():Uri? {
        if (uri==null)
            {return null}
        else
            {return Uri.parse(uri)}}
}