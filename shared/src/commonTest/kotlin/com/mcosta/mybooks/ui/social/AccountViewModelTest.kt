package com.mcosta.mybooks.ui.social

import com.mcosta.mybooks.MainDispatcherTest
import com.mcosta.mybooks.data.covers.PhotoShrinker
import com.mcosta.mybooks.social.FakeSocial
import com.mcosta.mybooks.social.model.SessionState
import com.mcosta.mybooks.social.model.Visibility
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountViewModelTest : MainDispatcherTest() {
    private val fake = FakeSocial()
    private val ana = fake.addUser("ana")
    private val carla = fake.addUser("carla")

    private class RecordingShrinker : PhotoShrinker {
        var maxSide: Int? = null
        override fun shrink(photo: ByteArray, maxSide: Int): ByteArray {
            this.maxSide = maxSide
            return byteArrayOf(9, 9)
        }
    }

    private val shrinker = RecordingShrinker()

    private fun vm() = AccountViewModel(fake.services, shrinker, workDispatcher = mainDispatcher)

    @Test
    fun loadsProfileAndBlocked() = runTest {
        fake.signInAs(ana)
        fake.safetyService.block(carla)
        val vm = vm()
        assertEquals("ana", vm.state.value.profile?.username)
        assertEquals(listOf("carla"), vm.state.value.blocked.map { it.username })
    }

    @Test
    fun visibilityChangeIsSaved() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.setVisibility(Visibility.PUBLIC)
        assertEquals(Visibility.PUBLIC, vm.state.value.profile?.visibility)
        assertEquals(Visibility.PUBLIC, fake.profileService.myProfile()?.visibility)
    }

    @Test
    fun displayNameIsSavedTrimmed() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.saveDisplayName("  Ana Rita ")
        assertEquals("Ana Rita", fake.profileService.myProfile()?.displayName)
    }

    @Test
    fun blankDisplayNameRejected() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.saveDisplayName("   ")
        assertEquals("Enter a name", vm.state.value.message)
        assertEquals("Ana", fake.profileService.myProfile()?.displayName)
    }

    @Test
    fun avatarIsShrunkTo512BeforeUpload() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.setAvatar(byteArrayOf(1, 2, 3))
        assertEquals(512, shrinker.maxSide)
        assertContentEquals(byteArrayOf(9, 9), fake.uploadedAvatars.single())
        assertTrue(vm.state.value.profile?.avatarUrl != null)
    }

    @Test
    fun unblockRemovesFromList() = runTest {
        fake.signInAs(ana)
        fake.safetyService.block(carla)
        val vm = vm()
        vm.unblock(carla)
        assertTrue(vm.state.value.blocked.isEmpty())
    }

    @Test
    fun signOutEndsSession() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.signOut()
        assertEquals(SessionState.SignedOut, fake.auth.session.value)
        assertEquals(null, vm.state.value.profile)
        assertTrue(vm.state.value.signedOut)
    }

    @Test
    fun deleteAccountSignsOutAndRemovesProfile() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.deleteAccount()
        assertTrue(vm.state.value.deleted)
        assertEquals(SessionState.SignedOut, fake.auth.session.value)
        fake.signInAs(carla)
        assertEquals(null, fake.profileService.person(ana))
    }

    @Test
    fun offlineShowsMessage() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        fake.offline = true
        vm.setVisibility(Visibility.PUBLIC)
        assertEquals("You're offline", vm.state.value.message)
    }
}
