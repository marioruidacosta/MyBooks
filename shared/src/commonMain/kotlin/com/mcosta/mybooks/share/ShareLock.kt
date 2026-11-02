package com.mcosta.mybooks.share

import kotlinx.coroutines.sync.Mutex

/**
 * Serialises changes to share_state between ShareTracker (library edits) and Publisher (after an upload),
 * so neither overwrites a row the other has just changed. Never held during a network call.
 */
class ShareLock {
    internal val mutex = Mutex()
}
