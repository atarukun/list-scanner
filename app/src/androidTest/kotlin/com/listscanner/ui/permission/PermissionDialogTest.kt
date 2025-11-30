package com.listscanner.ui.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.listscanner.ui.components.PermissionRationaleDialog
import org.junit.Rule
import org.junit.Test

class PermissionDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rationaleDialog_displaysCorrectTitle() {
        composeTestRule.setContent {
            PermissionRationaleDialog(
                onTryAgain = {},
                onOpenSettings = {},
                onDismiss = {}
            )
        }

        composeTestRule
            .onNodeWithText("Camera Permission Required")
            .assertIsDisplayed()
    }

    @Test
    fun rationaleDialog_displaysCorrectMessage() {
        composeTestRule.setContent {
            PermissionRationaleDialog(
                onTryAgain = {},
                onOpenSettings = {},
                onDismiss = {}
            )
        }

        composeTestRule
            .onNodeWithText("List Scanner needs camera access", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun rationaleDialog_showsTryAgainButton_whenNotPermanentlyDenied() {
        composeTestRule.setContent {
            PermissionRationaleDialog(
                showOpenSettings = false,
                onTryAgain = {},
                onOpenSettings = {},
                onDismiss = {}
            )
        }

        composeTestRule
            .onNodeWithText("Try Again")
            .assertIsDisplayed()
    }

    @Test
    fun rationaleDialog_tryAgainButton_isClickable() {
        var clicked = false

        composeTestRule.setContent {
            PermissionRationaleDialog(
                showOpenSettings = false,
                onTryAgain = { clicked = true },
                onOpenSettings = {},
                onDismiss = {}
            )
        }

        composeTestRule.onNodeWithText("Try Again").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun rationaleDialog_showsOpenSettingsButton_whenPermanentlyDenied() {
        composeTestRule.setContent {
            PermissionRationaleDialog(
                showOpenSettings = true,
                onTryAgain = {},
                onOpenSettings = {},
                onDismiss = {}
            )
        }

        composeTestRule
            .onNodeWithText("Open Settings")
            .assertIsDisplayed()
    }

    @Test
    fun rationaleDialog_openSettingsButton_isClickable() {
        var clicked = false

        composeTestRule.setContent {
            PermissionRationaleDialog(
                showOpenSettings = true,
                onTryAgain = {},
                onOpenSettings = { clicked = true },
                onDismiss = {}
            )
        }

        composeTestRule.onNodeWithText("Open Settings").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun rationaleDialog_cancelButton_isDisplayed() {
        composeTestRule.setContent {
            PermissionRationaleDialog(
                onTryAgain = {},
                onOpenSettings = {},
                onDismiss = {}
            )
        }

        composeTestRule
            .onNodeWithText("Cancel")
            .assertIsDisplayed()
    }

    @Test
    fun rationaleDialog_cancelButton_callsOnDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            PermissionRationaleDialog(
                onTryAgain = {},
                onOpenSettings = {},
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertThat(dismissed).isTrue()
    }
}
