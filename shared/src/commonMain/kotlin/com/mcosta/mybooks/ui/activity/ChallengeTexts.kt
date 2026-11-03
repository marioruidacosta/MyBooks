package com.mcosta.mybooks.ui.activity

import com.mcosta.mybooks.domain.ChallengeProgress
import com.mcosta.mybooks.domain.Pace

private fun bookCount(n: Int) = if (n == 1) "1 book" else "$n books"

/** "2 books ahead of schedule", "Challenge complete!", … */
fun Pace.text(): String = when (this) {
    Pace.Complete -> "Challenge complete!"
    Pace.Upcoming -> "Starts on 1 January"
    is Pace.Ended -> "Ended ${bookCount(short)} short"
    Pace.OnTrack -> "On track"
    is Pace.Ahead -> "${bookCount(books)} ahead of schedule"
    is Pace.Behind -> "${bookCount(books)} behind schedule"
}

/** "18 of 30 books". */
fun ChallengeProgress.countText(): String = "$read of ${bookCount(goal)}"
