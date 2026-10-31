package com.afyzfur.afyzhub.util

import android.content.Context
import androidx.annotation.StringRes

/** Shared resource lookup for UI callbacks/ViewModels as well as composables. */
object AppText {
    private lateinit var application: Context
    fun initialize(context: Context) { application = context.applicationContext }
    fun get(@StringRes id: Int): String = application.getString(id)
}