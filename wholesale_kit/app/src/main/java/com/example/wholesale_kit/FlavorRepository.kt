package com.example.wholesale_kit

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object FlavorRepository {

    fun loadFlavors(context: Context): List<Flavor> {
        val inputStream = context.assets.open("flavors.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<Flavor>>() {}.type
        return Gson().fromJson(json, type)
    }
}