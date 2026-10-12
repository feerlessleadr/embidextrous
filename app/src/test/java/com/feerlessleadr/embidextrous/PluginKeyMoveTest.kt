package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.SecureStore
import com.feerlessleadr.embidextrous.data.SessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Hall Monitor key is stored under a new name from 0.5; a key saved by an older version must still be found, once moved. */
class PluginKeyMoveTest {
    private class MapStore : SecureStore {
        val plain = HashMap<String, String>()
        val secret = HashMap<String, String>()
        override fun get(name: String) = plain[name]
        override fun put(name: String, value: String) { plain[name] = value }
        override fun getSecret(name: String) = secret[name]
        override fun putSecret(name: String, value: String) { secret[name] = value }
        override fun clear() { plain.clear(); secret.clear() }
    }

    @Test fun aKeySavedByAnOlderVersionIsFoundAndMoved() {
        val store = MapStore()
        store.putSecret("watchingEyeKey", "key-from-before")
        val repo = SessionRepository(store)
        assertEquals("key-from-before", repo.loadPluginKey())
        // moved: the new name holds it and the old one is emptied
        assertEquals("key-from-before", store.secret["hallMonitorKey"])
        assertEquals("", store.secret["watchingEyeKey"])
        assertEquals("key-from-before", repo.loadPluginKey())
    }

    @Test fun theNewNameWinsAndNothingStoredMeansNoKey() {
        val store = MapStore()
        assertNull(SessionRepository(store).loadPluginKey())
        store.putSecret("watchingEyeKey", "old")
        store.putSecret("hallMonitorKey", "new")
        assertEquals("new", SessionRepository(store).loadPluginKey())
    }

    @Test fun savingAndClearingUseTheNewName() {
        val store = MapStore()
        val repo = SessionRepository(store)
        repo.savePluginKey("  abc  ")
        assertEquals("abc", repo.loadPluginKey())
        repo.savePluginKey("")
        assertNull(repo.loadPluginKey())
    }
}
