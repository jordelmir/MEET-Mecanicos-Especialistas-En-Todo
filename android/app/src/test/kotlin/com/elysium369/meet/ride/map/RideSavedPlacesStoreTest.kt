package com.elysium369.meet.ride.map

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSavedPlacesStoreTest {
    @Test
    fun `account switch and new store never expose another account home or legacy places`() {
        val values = mutableMapOf<String, String>("places" to """[{"slot":"HOME","label":"legacy","address":"private","latitude":1.0,"longitude":2.0,"providerId":"legacy"}]""")
        val preferences = preferences(values)
        val store = RideSavedPlacesStore(preferences)
        assertTrue(store.load("user-A").isEmpty())
        val home = RideSavedPlace("HOME", "Casa", "Private A", 1.0, 2.0, "a")
        val work = home.copy(slot = "WORK", address = "Work A")
        store.save("user-A", home)
        store.save("user-A", work)
        assertTrue(store.load(null).isEmpty())
        assertTrue(store.load("user-B").isEmpty())
        store.save("user-B", home.copy(address = "Private B"))
        val restarted = RideSavedPlacesStore(preferences)
        assertEquals(listOf(home, work), restarted.load("user-A"))
        assertEquals("Private B", restarted.load("user-B").single().address)
        assertEquals(3, values.size)
    }

    @Test
    fun `anonymous save is rejected without writing a device owner slot`() {
        val values = mutableMapOf<String, String>()
        val store = RideSavedPlacesStore(preferences(values))
        try {
            store.save(null, RideSavedPlace("HOME", "Casa", "Private", 1.0, 2.0, "a"))
            throw AssertionError("Anonymous save must fail")
        } catch (_: IllegalArgumentException) { }
        assertTrue(values.isEmpty())
        assertEquals(null, ridePrivatePreferenceKey(" ", "home_latitude"))
        assertNotEquals(ridePrivatePreferenceKey("user-A", "home_latitude"), ridePrivatePreferenceKey("user-B", "home_latitude"))
    }

    private fun preferences(values: MutableMap<String, String>): SharedPreferences {
        val pending = mutableMapOf<String, String>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { pending[args!![0] as String] = args[1] as String; editor }
                "apply", "commit" -> { values.putAll(pending); pending.clear(); if (method.name == "commit") true else null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences
    }
}
