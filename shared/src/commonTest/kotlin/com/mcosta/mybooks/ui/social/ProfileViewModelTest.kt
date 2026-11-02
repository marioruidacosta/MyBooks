package com.mcosta.mybooks.ui.social

import com.mcosta.mybooks.MainDispatcherTest
import com.mcosta.mybooks.social.FakeSocial
import com.mcosta.mybooks.social.model.ActivityKind
import com.mcosta.mybooks.social.model.Visibility
import com.mcosta.mybooks.social.model.Relationship
import com.mcosta.mybooks.social.model.ReportReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileViewModelTest : MainDispatcherTest() {
    private val fake = FakeSocial()
    private val ana = fake.addUser("ana")
    private val bruno = fake.addUser("bruno")

    private fun vm(userId: String = bruno) = ProfileViewModel(fake.services, userId)

    @Test
    fun actionsFollowRelationship() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        assertEquals("bruno", vm.state.value.person?.username)
        assertEquals(Relationship.NONE, vm.state.value.relationship)
        vm.add()
        assertEquals(Relationship.REQUEST_SENT, vm.state.value.relationship)
        vm.cancel()
        assertEquals(Relationship.NONE, vm.state.value.relationship)
    }

    @Test
    fun acceptAndRemoveFriend() = runTest {
        fake.signInAs(bruno)
        fake.friendsService.sendRequest(ana)
        fake.signInAs(ana)
        val vm = vm()
        assertEquals(Relationship.REQUEST_RECEIVED, vm.state.value.relationship)
        vm.accept()
        assertEquals(Relationship.FRIENDS, vm.state.value.relationship)
        vm.removeFriend()
        assertEquals(Relationship.NONE, vm.state.value.relationship)
    }

    @Test
    fun declineRequest() = runTest {
        fake.signInAs(bruno)
        fake.friendsService.sendRequest(ana)
        fake.signInAs(ana)
        val vm = vm()
        vm.decline()
        assertEquals(Relationship.NONE, vm.state.value.relationship)
    }

    @Test
    fun blockLeavesScreen() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.block()
        assertTrue(vm.state.value.left)
        assertNull(fake.profileService.person(bruno))
    }

    @Test
    fun reportSendsReasonAndTrimmedDetails() = runTest {
        fake.signInAs(ana)
        val vm = vm()
        vm.report(ReportReason.SPAM, "  sells followers  ")
        assertEquals(FakeSocial.Report(ana, bruno, ReportReason.SPAM, "sells followers"), fake.reports.single())
        assertEquals("Thanks, we'll look into it", vm.state.value.message)
    }

    @Test
    fun blankReportDetailsSentAsNull() = runTest {
        fake.signInAs(ana)
        vm().report(ReportReason.OTHER, "   ")
        assertNull(fake.reports.single().details)
    }

    @Test
    fun reportDetailsLimitedTo1000() = runTest {
        fake.signInAs(ana)
        vm().report(ReportReason.HARASSMENT, "x".repeat(1500))
        assertEquals(1000, fake.reports.single().details?.length)
    }

    @Test
    fun blockedProfileShowsNotAvailable() = runTest {
        fake.signInAs(bruno)
        fake.safetyService.block(ana)
        fake.signInAs(ana)
        val vm = vm()
        assertTrue(vm.state.value.notAvailable)
        assertNull(vm.state.value.person)
    }

    @Test
    fun ownProfileHasNoFriendActions() = runTest {
        fake.signInAs(ana)
        val vm = vm(ana)
        assertTrue(vm.state.value.isMe)
        assertFalse(vm.state.value.notAvailable)
    }

    @Test
    fun publicStrangerShowsActivity() = runTest {
        val carla = fake.addUser("carla").also { fake.setVisibility(it, Visibility.PUBLIC) }
        fake.addEntry(carla, ActivityKind.FINISHED, title = "Emma")
        fake.signInAs(ana)
        val state = vm(carla).state.value
        assertFalse(state.activityHidden)
        assertEquals(listOf("Emma"), state.activity.map { it.book.title })
    }

    @Test
    fun friendsOnlyStrangerShowsHidden() = runTest {
        fake.addEntry(bruno, ActivityKind.FINISHED)
        fake.signInAs(ana)
        val state = vm().state.value
        assertTrue(state.activityHidden)
        assertTrue(state.activity.isEmpty())
        assertEquals(0, fake.personActivityCalls)
    }

    @Test
    fun friendShowsActivity() = runTest {
        fake.makeFriends(ana, bruno)
        val id = fake.addEntry(bruno, ActivityKind.STARTED, title = "Dune")
        fake.signInAs(ana)
        val vm = vm()
        assertFalse(vm.state.value.activityHidden)
        assertEquals(listOf("Dune"), vm.state.value.activity.map { it.book.title })
        vm.toggleLike(id)
        assertTrue(vm.state.value.activity.single().likedByMe)
        assertEquals(1, vm.state.value.activity.single().likeCount)
    }

    @Test
    fun likersShownOnProfile() = runTest {
        fake.makeFriends(ana, bruno)
        val id = fake.addEntry(bruno, ActivityKind.STARTED)
        fake.signInAs(ana)
        fake.activityService.like(id)
        val vm = vm()
        vm.showLikers(id)
        assertEquals(listOf("ana"), vm.state.value.likers?.map { it.username })
        vm.dismissLikers()
        assertNull(vm.state.value.likers)
    }
}
