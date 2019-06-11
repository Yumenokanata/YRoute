package indi.yume.tools.yroute

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import io.kotlintest.assertSoftly
import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.matchers.string.contain
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.properties.forAll
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrowAny
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoboTest {
    @get:Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun testProp() {
//        onView(withId(R.id.button)).perform(click())
//
//        onView(withId(R.id.text_view))
//            .check(ViewAssertions.matches(ViewMatchers.withText("test")))
//
//        shouldThrowAny {
//            assertAll(Gen.string(), Gen.string()) { a, b ->
//                (a.length + b.length).shouldBeLessThan(4)
//            }
//        }.message shouldBe "Property failed for\n" +
//                "Arg 0: <empty string>\n" +
//                "Arg 1: aaaaa (shrunk from \n" +
//                "abc\n" +
//                "123\n" +
//                ")\n" +
//                "after 3 attempts\n" +
//                "Caused by: 9 should be < 4"
//
//        forAll { a: Int ->
//            2 * a % 2 == 0
//        }
//
//        assertSoftly {
//            "" shouldBe ""
//            "" should contain("")
//        }

        val map = mapOf("1" to 1, "2" to 2, "3" to 3, "4" to 4)


    }
}