package com.mcosta.mybooks.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UsernamesTest {
    @Test fun normalizesCaseAndSpaces() = assertEquals("mario.c", normalizeUsername(" Mario.C "))

    @Test fun tooShortIsAProblem() { assertNotNull(usernameProblem("ab")) }

    @Test fun spaceIsAProblem() { assertNotNull(usernameProblem("has space")) }

    @Test fun tooLongIsAProblem() { assertNotNull(usernameProblem("x".repeat(21))) }

    @Test fun validUsername() = assertNull(usernameProblem("mario_c.1"))

    @Test fun problemTextExplainsTheRule() = assertEquals("3–20 characters: a–z, 0–9, _ and .", usernameProblem("ab"))

    @Test
    fun messagesForEachKind() {
        assertEquals("You're offline", socialMessage(SocialException(SocialException.Kind.OFFLINE, "x")))
        assertEquals("That code is wrong or has expired", socialMessage(SocialException(SocialException.Kind.INVALID_CODE, "x")))
        assertEquals("That username is taken", socialMessage(SocialException(SocialException.Kind.USERNAME_TAKEN, "x")))
        assertEquals("This profile isn't available", socialMessage(SocialException(SocialException.Kind.NOT_AVAILABLE, "x")))
        assertEquals("Something went wrong, please try again", socialMessage(SocialException(SocialException.Kind.OTHER, "x")))
        assertEquals("Something went wrong, please try again", socialMessage(IllegalStateException("boom")))
    }
}
