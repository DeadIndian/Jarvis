package com.jarvis.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun launchInputAndActionFlowShowsActiveStateAndOutputLog() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.inputText)).perform(replaceText("open appthatdoesnotexist"))
            closeSoftKeyboard()
            onView(withId(R.id.sendButton)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(1000)

            onView(withId(R.id.statusText)).check(matches(withText(containsString("ACTIVE"))))
            onView(withId(R.id.logText)).check(matches(withText(containsString("Event: TextInput"))))
            onView(withId(R.id.logText)).check(matches(withText(containsString("Jarvis:"))))
        }
    }
}
