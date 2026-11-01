package com.mcosta.mybooks.social.supabase

import com.mcosta.mybooks.social.model.Person
import com.mcosta.mybooks.social.model.Profile
import com.mcosta.mybooks.social.model.Relationship
import com.mcosta.mybooks.social.model.Visibility
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Rows as PostgREST returns them (column names from supabase/migrations). */
@Serializable
internal data class ProfileRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    val visibility: String = "friends",
)

@Serializable
internal data class NewProfileRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
internal data class FriendshipRow(val requester: String, val addressee: String, val status: String)

@Serializable
internal data class NewReportRow(val reporter: String, val reported: String, val reason: String, val details: String?)

internal fun visibilityOf(value: String) = if (value == "public") Visibility.PUBLIC else Visibility.FRIENDS

internal fun Visibility.dbValue() = if (this == Visibility.PUBLIC) "public" else "friends"

internal fun ProfileRow.toProfile(avatarUrl: (String?) -> String?) =
    Profile(id, username, displayName, avatarUrl(avatarPath), visibilityOf(visibility))

internal fun ProfileRow.toPerson(avatarUrl: (String?) -> String?) = Person(id, username, displayName, avatarUrl(avatarPath))

/** How [me] relates to [other], from the friendship rows visible to me. */
internal fun relationshipFrom(rows: List<FriendshipRow>, me: String, other: String): Relationship {
    val row = rows.firstOrNull { setOf(it.requester, it.addressee) == setOf(me, other) } ?: return Relationship.NONE
    return when {
        row.status == "accepted" -> Relationship.FRIENDS
        row.requester == me -> Relationship.REQUEST_SENT
        else -> Relationship.REQUEST_RECEIVED
    }
}
