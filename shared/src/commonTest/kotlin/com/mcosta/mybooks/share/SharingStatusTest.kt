@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mcosta.mybooks.share

import com.mcosta.mybooks.social.FakeSocial
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SharingStatusTest {
    private val fake = FakeSocial()
    private val ana = fake.addUser("ana")
    private var activations = 0
    private val disabled = mutableListOf<String>()

    private fun TestScope.status() = SharingStatus(
        fake.services, backgroundScope,
        onActivated = { activations++ },
        onDisabled = { disabled += it },
    ).also { runCurrent() }

    @Test
    fun activeOnlyWithProfileAndShareActivity() = runTest {
        val status = status()
        assertNull(status.state.value.activeOwner)
        fake.signInAs("id-no-profile-yet")
        runCurrent()
        assertNull(status.state.value.activeOwner)
        assertFalse(status.state.value.hasProfile)
        fake.signInAs(ana)
        runCurrent()
        assertEquals(ana, status.state.value.activeOwner)
        assertTrue(status.state.value.hasProfile)
        status.setShareActivity(false)
        assertNull(status.state.value.activeOwner)
        assertTrue(status.state.value.hasProfile)
    }

    @Test
    fun disablingCallsServerThenForgetsLocalRows() = runTest {
        fake.signInAs(ana)
        val status = status()
        val profile = status.setShareActivity(false)
        assertFalse(profile.shareActivity)
        assertFalse(fake.profileService.myProfile()!!.shareActivity)
        assertEquals(listOf(ana), disabled)
    }

    @Test
    fun failedDisableKeepsLocalRows() = runTest {
        fake.signInAs(ana)
        val status = status()
        fake.offline = true
        runCatching { status.setShareActivity(false) }
        assertTrue(disabled.isEmpty())
        assertEquals(ana, status.state.value.activeOwner)
    }

    @Test
    fun activationTriggersSync() = runTest {
        val status = status()
        assertEquals(0, activations)
        fake.signInAs(ana)
        runCurrent()
        assertEquals(1, activations)
        status.setShareActivity(false)
        status.setShareActivity(true)
        assertEquals(2, activations)
    }

    @Test
    fun refreshPicksUpNewProfile() = runTest {
        fake.signInAs("id-new-0")
        val status = status()
        assertNull(status.state.value.activeOwner)
        fake.profileService.createProfile("newbie", "Newbie")
        status.refresh()
        assertEquals("id-new-0", status.state.value.activeOwner)
    }

    /** Offline at app start: keep tracking (the server ignores publishes when sharing is off) and retry. */
    @Test
    fun unknownProfileAssumesSharingAndRetries() = runTest {
        fake.offline = true
        fake.signInAs(ana)
        val status = status()
        assertEquals(ana, status.state.value.activeOwner)
        assertFalse(status.state.value.confirmed)
        fake.offline = false
        advanceTimeBy(61.seconds)
        runCurrent()
        assertTrue(status.state.value.confirmed)
        assertEquals(ana, status.state.value.activeOwner)
    }
}
