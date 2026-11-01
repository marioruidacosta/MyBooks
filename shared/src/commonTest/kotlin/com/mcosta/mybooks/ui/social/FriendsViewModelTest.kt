package com.mcosta.mybooks.ui.social

import com.mcosta.mybooks.MainDispatcherTest
import com.mcosta.mybooks.social.FakeSocial
import com.mcosta.mybooks.social.model.Relationship
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FriendsViewModelTest : MainDispatcherTest() {
    private val fake = FakeSocial()
    private val ana = fake.addUser("ana")
    private val bruno = fake.addUser("bruno")
    private val carla = fake.addUser("carla")
    private val dora = fake.addUser("dora")

    /** Makes [from] send a friend request to [to]. */
    private suspend fun request(from: String, to: String) {
        fake.signInAs(from)
        fake.friendsService.sendRequest(to)
    }

    private fun vm() = FriendsViewModel(fake.services)

    @Test
    fun signedOutShowsSignInPrompt() = runTest {
        val vm = vm()
        assertFalse(vm.state.value.signedIn)
        assertEquals(0, fake.searchCalls)
    }

    @Test
    fun loadsFriendsAndRequests() = runTest {
        request(bruno, ana)
        request(ana, carla)
        val vm = vm()
        assertTrue(vm.state.value.signedIn)
        assertEquals(listOf("bruno"), vm.state.value.incoming.map { it.username })
        assertEquals(listOf("carla"), vm.state.value.outgoing.map { it.username })
    }

    @Test
    fun acceptMovesPersonToFriends() = runTest {
        request(bruno, ana)
        fake.signInAs(ana)
        val vm = vm()
        vm.accept(bruno)
        assertEquals(listOf("bruno"), vm.state.value.friends.map { it.username })
        assertTrue(vm.state.value.incoming.isEmpty())
    }

    @Test
    fun declineRemovesRequest() = runTest {
        request(bruno, ana)
        fake.signInAs(ana)
        val vm = vm()
        vm.decline(bruno)
        assertTrue(vm.state.value.incoming.isEmpty())
        assertTrue(vm.state.value.friends.isEmpty())
    }

    @Test
    fun cancelRemovesOutgoing() = runTest {
        request(ana, carla)
        val vm = vm()
        vm.cancel(carla)
        assertTrue(vm.state.value.outgoing.isEmpty())
        assertEquals(Relationship.NONE, fake.relationshipBetween(ana, carla))
    }

    @Test
    fun searchWaitsForTwoCharsAndDebounces() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.setQuery("b")
        advanceTimeBy(301)
        assertEquals(0, fake.searchCalls)
        vm.setQuery("br")
        vm.setQuery("bru")
        vm.setQuery("brun")
        advanceTimeBy(301)
        assertEquals(1, fake.searchCalls)
        assertEquals(listOf("bruno"), vm.state.value.results.map { it.person.username })
    }

    @Test
    fun searchResultLabelsFollowRelationship() = runTest {
        request(dora, ana)
        request(ana, carla)
        val vm = vm()
        vm.setQuery("carla")
        advanceTimeBy(301)
        assertEquals(Relationship.REQUEST_SENT, vm.state.value.results.single().relationship)
        vm.setQuery("dora")
        advanceTimeBy(301)
        assertEquals(Relationship.REQUEST_RECEIVED, vm.state.value.results.single().relationship)
    }

    @Test
    fun addFromSearchUpdatesLabel() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.setQuery("bruno")
        advanceTimeBy(301)
        vm.add(bruno)
        assertEquals(Relationship.REQUEST_SENT, vm.state.value.results.single().relationship)
        assertEquals(listOf("bruno"), vm.state.value.outgoing.map { it.username })
    }

    @Test
    fun offlineKeepsLastListsAndShowsRetry() = runTest {
        request(bruno, ana)
        fake.signInAs(ana)
        val vm = vm()
        fake.offline = true
        vm.refresh()
        assertEquals("You're offline", vm.state.value.message)
        assertEquals(listOf("bruno"), vm.state.value.incoming.map { it.username })
    }
}
