package com.listscanner.ui.screens.camera

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("CameraPermissionViewModel")
class CameraPermissionViewModelTest {

    private lateinit var viewModel: CameraPermissionViewModel

    @BeforeEach
    fun setup() {
        viewModel = CameraPermissionViewModel()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `initial state is NotRequested`() = runTest {
            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.NotRequested)
        }

        @Test
        fun `StateFlow emits initial NotRequested state`() = runTest {
            val state = viewModel.uiState.first()
            assertThat(state).isEqualTo(CameraPermissionViewModel.UiState.NotRequested)
        }
    }

    @Nested
    @DisplayName("onPermissionResult")
    inner class OnPermissionResult {

        @Test
        fun `granted true updates state to Granted`() = runTest {
            viewModel.onPermissionResult(granted = true, shouldShowRationale = false)

            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.Granted)
        }

        @Test
        fun `granted true ignores shouldShowRationale`() = runTest {
            viewModel.onPermissionResult(granted = true, shouldShowRationale = true)

            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.Granted)
        }

        @Test
        fun `denied with rationale updates state to Denied`() = runTest {
            viewModel.onPermissionResult(granted = false, shouldShowRationale = true)

            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.Denied)
        }

        @Test
        fun `denied without rationale updates state to PermanentlyDenied`() = runTest {
            viewModel.onPermissionResult(granted = false, shouldShowRationale = false)

            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.PermanentlyDenied)
        }
    }

    @Nested
    @DisplayName("resetToNotRequested")
    inner class ResetToNotRequested {

        @Test
        fun `resets Denied state to NotRequested`() = runTest {
            viewModel.onPermissionResult(granted = false, shouldShowRationale = true)
            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.Denied)

            viewModel.resetToNotRequested()

            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.NotRequested)
        }

        @Test
        fun `resets PermanentlyDenied state to NotRequested`() = runTest {
            viewModel.onPermissionResult(granted = false, shouldShowRationale = false)
            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.PermanentlyDenied)

            viewModel.resetToNotRequested()

            assertThat(viewModel.uiState.value)
                .isEqualTo(CameraPermissionViewModel.UiState.NotRequested)
        }
    }

    @Nested
    @DisplayName("state transitions")
    inner class StateTransitions {

        @Test
        fun `state updates trigger new emissions via StateFlow`() = runTest {
            val collectedStates = mutableListOf<CameraPermissionViewModel.UiState>()

            // Collect initial state
            collectedStates.add(viewModel.uiState.value)

            // Transition to Granted
            viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
            collectedStates.add(viewModel.uiState.value)

            // Reset
            viewModel.resetToNotRequested()
            collectedStates.add(viewModel.uiState.value)

            // Transition to Denied
            viewModel.onPermissionResult(granted = false, shouldShowRationale = true)
            collectedStates.add(viewModel.uiState.value)

            assertThat(collectedStates).containsExactly(
                CameraPermissionViewModel.UiState.NotRequested,
                CameraPermissionViewModel.UiState.Granted,
                CameraPermissionViewModel.UiState.NotRequested,
                CameraPermissionViewModel.UiState.Denied
            ).inOrder()
        }
    }
}
