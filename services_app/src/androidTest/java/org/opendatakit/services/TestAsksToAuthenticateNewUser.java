package org.opendatakit.services;

import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class TestAsksToAuthenticateNewUser {

  @Rule
  public ActivityTestRule<MainActivity> mActivityTestRule = new ActivityTestRule<>(
      MainActivity.class);

  @Test
  public void testAsksToAuthenticateNewUser() {
    ViewInteraction overflowMenuButton = onView(
        allOf(withContentDescription("More options"), isDisplayed()));
    overflowMenuButton.perform(click());

    ViewInteraction textView = onView(
        allOf(withId(android.R.id.title), withText("Settings"), isDisplayed()));
    textView.perform(click());

    ViewInteraction linearLayout = onView(allOf(childAtPosition(allOf(withId(android.R.id.list),
        withParent(withClassName(is("android.widget.LinearLayout")))), 1), isDisplayed()));
    linearLayout.perform(click());

    ViewInteraction linearLayout2 = onView(
        allOf(childAtPosition(withId(android.R.id.list), 2), isDisplayed()));
    linearLayout2.perform(click());

    ViewInteraction checkedTextView = onView(allOf(withId(android.R.id.text1), withText("Username"),
        childAtPosition(
            allOf(withClassName(is("com.android.internal.app.AlertController$RecycleListView")),
                withParent(withClassName(is("android.widget.LinearLayout")))), 1), isDisplayed()));
    checkedTextView.perform(click());

    ViewInteraction linearLayout3 = onView(
        allOf(childAtPosition(withId(android.R.id.list), 3), isDisplayed()));
    linearLayout3.perform(click());

    ViewInteraction editText = onView(allOf(withId(android.R.id.edit),
        withParent(withClassName(is("android.widget.LinearLayout")))));
    editText.perform(scrollTo(), click());

    ViewInteraction button = onView(
        allOf(withId(android.R.id.button1), withText("OK"), isDisplayed()));
    button.perform(click());

    pressBack();

    ViewInteraction frameLayout = onView(
        allOf(IsInstanceOf.<View>instanceOf(android.widget.FrameLayout.class), isDisplayed()));
    frameLayout.check(matches(isDisplayed()));

    ViewInteraction textView2 = onView(
        allOf(IsInstanceOf.<View>instanceOf(android.widget.TextView.class),
            withText("Authenticate Credentials"), childAtPosition(
                allOf(IsInstanceOf.<View>instanceOf(android.widget.LinearLayout.class),
                    childAtPosition(
                        IsInstanceOf.<View>instanceOf(android.widget.LinearLayout.class), 0)), 0),
            isDisplayed()));
    textView2.check(matches(withText("Authenticate Credentials")));

    ViewInteraction button2 = onView(
        allOf(withId(android.R.id.button2), withText("Log Out"), isDisplayed()));
    button2.perform(click());

  }

  private static Matcher<View> childAtPosition(final Matcher<View> parentMatcher,
      final int position) {

    return new TypeSafeMatcher<View>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Child at position " + position + " in parent ");
        parentMatcher.describeTo(description);
      }

      @Override
      public boolean matchesSafely(View view) {
        ViewParent parent = view.getParent();
        return parent instanceof ViewGroup && parentMatcher.matches(parent) && view
            .equals(((ViewGroup) parent).getChildAt(position));
      }
    };
  }
}
