package org.odk.collect.android.support.pages

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.odk.collect.shared.TimeInMs
import org.odk.collect.testshared.WaitFor.waitFor
import java.util.regex.Pattern

class NotificationDrawer {
    private var isOpen = false

    fun open(): NotificationDrawer {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.openNotification()

        assertThat(
            "Couldn't open notification drawer!",
            device.wait(Until.hasObject(DRAWER_SEARCH_CONDITION), TimeInMs.ONE_SECOND),
            equalTo(true)
        )
        isOpen = true
        return this
    }

    fun teardown() {
        if (!isOpen) {
            open()
        }

        clearAll()
    }

    @JvmOverloads
    fun assertNotification(
        appName: String,
        title: String,
        subtext: String? = null,
        body: String? = null
    ): NotificationDrawer {
        val device = waitForNotification(appName, title)

        if (subtext != null) {
            assertExpandedText(device, appName, subtext)
        }

        if (body != null) {
            assertExpandedText(device, appName, body)
        }

        return this
    }

    fun <D : Page<D>> clickAction(
        appName: String,
        title: String,
        actionText: String,
        destination: D
    ): D {
        val device = waitForNotification(appName, title)

        val actionElement = getExpandedElement(device, appName, actionText) ?: getExpandedElement(
            device,
            appName,
            actionText.uppercase()
        )
        if (actionElement != null) {
            actionElement.click()
            ensureClosed()
        } else {
            throw AssertionError("Could not find \"$actionText\"")
        }

        val page = waitFor {
            destination.assertOnPage()
        }

        assertNoNotification(appName)
        return page
    }

    fun <D : Page<D>> clickNotification(
        appName: String,
        title: String,
        destination: D
    ): D {
        val device = waitForNotification(appName, title)
        device.findObject(By.text(title)).click()
        ensureClosed()

        return waitFor {
            destination.assertOnPage()
        }
    }

    fun clearAll() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val clearAll = device.findObject(By.text("Clear all"))
        if (clearAll != null) {
            clearAll.click()
            ensureClosed()
        } else {
            close()
        }
    }

    private fun assertNoNotification(appName: String) {
        open()

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val result = device.wait(Until.hasObject(By.textStartsWith(appName)), 0L)
        assertThat("Expected no notification for app: $appName", result, equalTo(false))

        close()
    }

    private fun assertExpandedText(
        device: UiDevice,
        appName: String,
        text: String
    ) {
        val element = getExpandedElement(device, appName, text)
        assertThat("Could not find \"$text\"", element, not(nullValue()))
    }

    private fun getExpandedElement(
        device: UiDevice,
        appName: String,
        text: String
    ): UiObject2? {
        var element = device.findObject(By.text(text))
        if (element == null) {
            expandOrCollapseNotification(device, appName)
            element = device.findObject(By.text(text))
        }
        return element
    }

    private fun expandOrCollapseNotification(device: UiDevice, appName: String) {
        device.findObject(By.text(appName)).click()
    }

    private fun waitForNotification(appName: String, title: String): UiDevice {
        return waitFor {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val result = device.wait(Until.hasObject(By.text(appName)), 0L) && device.wait(Until.hasObject(By.text(title)), 0L)
            assertThat(
                "No notification for app: $appName with title $title",
                result,
                `is`(true)
            )
            device
        }
    }

    private fun close() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (isOpen) {
            device.pressBack()
        }

        ensureClosed()
    }

    /**
     * It appears that sometimes the notification drawer does not close automatically when it should
     * such as after clicking on a notification or its action. This could be due to a bug in Android.
     */
    private fun ensureClosed() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (!device.wait(Until.gone(DRAWER_SEARCH_CONDITION), TimeInMs.ONE_SECOND * 3)) {
            device.pressBack()
            assertThat(
                "Couldn't close notification drawer!",
                device.wait(Until.gone(DRAWER_SEARCH_CONDITION), TimeInMs.ONE_SECOND),
                equalTo(true)
            )
        }

        this.isOpen = false
    }

    companion object {
        val SUPPORTED_SDKS = listOf(30, 34)

        private val DRAWER_SEARCH_CONDITION = By.text(Pattern.compile("Manage|No notifications"))
    }
}
