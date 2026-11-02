package com.mcosta.mybooks.social

import com.mcosta.mybooks.social.model.ActivityKind
import com.mcosta.mybooks.social.model.FeedScope
import com.mcosta.mybooks.social.model.ReportReason
import com.mcosta.mybooks.social.model.Visibility
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The fake follows the SQL visibility rules, so ViewModel tests built on it mean something. */
class FakeActivityTest {
    private val fake = FakeSocial()
    private val ana = fake.addUser("ana")
    private val bruno = fake.addUser("bruno")
    private val carla = fake.addUser("carla").also { fake.setVisibility(it, Visibility.PUBLIC) }
    private val dora = fake.addUser("dora")

    @Test
    fun friendsFeedHasFriendsAndMe() = runTest {
        fake.makeFriends(ana, bruno)
        fake.addEntry(ana, ActivityKind.STARTED)
        fake.addEntry(bruno, ActivityKind.FINISHED)
        fake.addEntry(carla, ActivityKind.RATED)
        fake.addEntry(dora, ActivityKind.REVIEWED)
        fake.signInAs(ana)
        val actors = fake.activityService.feed(FeedScope.FRIENDS).entries.map { it.actor.username }
        assertEquals(listOf("bruno", "ana"), actors)
    }

    @Test
    fun everyoneHasOnlyPublicUsers() = runTest {
        fake.makeFriends(ana, bruno)
        fake.addEntry(bruno, ActivityKind.FINISHED)
        fake.addEntry(carla, ActivityKind.RATED)
        fake.addEntry(dora, ActivityKind.REVIEWED)
        fake.signInAs(ana)
        assertEquals(listOf("carla"), fake.activityService.feed(FeedScope.EVERYONE).entries.map { it.actor.username })
        assertEquals(1, fake.activityService.personActivity(carla).entries.size)
        assertEquals(0, fake.activityService.personActivity(dora).entries.size)
    }

    @Test
    fun blockedHiddenBothWays() = runTest {
        fake.addEntry(carla, ActivityKind.RATED)
        fake.addEntry(ana, ActivityKind.RATED)
        fake.setVisibility(ana, Visibility.PUBLIC)
        fake.signInAs(carla)
        fake.safetyService.block(ana)
        assertEquals(listOf("carla"), fake.activityService.feed(FeedScope.EVERYONE).entries.map { it.actor.username })
        fake.signInAs(ana)
        assertEquals(listOf("ana"), fake.activityService.feed(FeedScope.EVERYONE).entries.map { it.actor.username })
    }

    @Test
    fun cannotLikeOwnEntry() = runTest {
        val id = fake.addEntry(carla, ActivityKind.RATED)
        fake.signInAs(carla)
        val e = assertFailsWith<SocialException> { fake.activityService.like(id) }
        assertEquals(SocialException.Kind.NOT_AVAILABLE, e.kind)
        fake.signInAs(ana)
        fake.activityService.like(id)
        fake.activityService.like(id)
        val entry = fake.activityService.feed(FeedScope.EVERYONE).entries.single()
        assertEquals(1, entry.likeCount)
        assertEquals(true, entry.likedByMe)
        assertEquals(listOf("ana"), fake.activityService.likers(id).map { it.username })
    }

    @Test
    fun pagingReturnsNextCursorOnlyWhenFull() = runTest {
        repeat(25) { fake.addEntry(carla, ActivityKind.PROGRESS, title = "Book $it") }
        fake.signInAs(ana)
        val first = fake.activityService.feed(FeedScope.EVERYONE)
        assertEquals(20, first.entries.size)
        assertEquals("Book 24", first.entries.first().book.title)
        assertNotNull(first.next)
        val second = fake.activityService.feed(FeedScope.EVERYONE, first.next)
        assertEquals(5, second.entries.size)
        assertEquals("Book 4", second.entries.first().book.title)
        assertNull(second.next)
    }

    @Test
    fun reportCarriesActivityId() = runTest {
        val id = fake.addEntry(carla, ActivityKind.REVIEWED)
        fake.signInAs(ana)
        fake.safetyService.report(carla, ReportReason.SPAM, null, activityId = id)
        assertEquals(id, fake.reports.single().activityId)
    }
}
