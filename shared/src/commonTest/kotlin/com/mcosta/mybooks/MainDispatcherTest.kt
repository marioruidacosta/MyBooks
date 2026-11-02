@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mcosta.mybooks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/** Base class for tests of ViewModels: replaces Dispatchers.Main (used by viewModelScope). */
abstract class MainDispatcherTest {
    val mainDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}
