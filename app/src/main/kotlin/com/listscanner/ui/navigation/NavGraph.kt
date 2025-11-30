package com.listscanner.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.listscanner.di.DatabaseModule
import com.listscanner.ui.components.PermissionRationaleDialog
import com.listscanner.ui.permission.CameraPermissionHandler
import com.listscanner.ui.permission.checkCameraPermission
import com.listscanner.ui.permission.shouldShowCameraRationale
import com.listscanner.ui.screens.camera.CameraCaptureScreen
import com.listscanner.ui.screens.camera.CameraPermissionViewModel
import com.listscanner.ui.screens.camera.CameraViewModel
import com.listscanner.ui.screens.gallery.PhotoGalleryScreen
import com.listscanner.ui.screens.gallery.PhotoGalleryViewModel
import com.listscanner.ui.screens.permission.PermissionDeniedScreen
import com.listscanner.ui.screens.review.PhotoReviewScreen
import com.listscanner.ui.screens.review.PhotoReviewViewModel
import com.listscanner.ui.screens.list.ListDetailScreen
import com.listscanner.ui.screens.list.ListDetailViewModel
import com.listscanner.ui.screens.listsoverview.ListsOverviewScreen
import com.listscanner.ui.screens.listsoverview.ListsOverviewViewModel
import com.listscanner.ui.screens.regionselection.RegionSelectionScreen
import com.listscanner.ui.screens.regionselection.RegionSelectionViewModel
import com.listscanner.ui.screens.regionselection.toCropRect
import com.listscanner.ui.screens.regionselection.toJson
import com.listscanner.ui.util.openAppSettings
import timber.log.Timber

/**
 * Main navigation graph for the app.
 *
 * @param navController Navigation controller, defaults to remembered instance
 * @param startDestination Starting route, defaults to PhotoGallery
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Destination.PhotoGallery.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destination.PhotoGallery.route) {
            val context = LocalContext.current
            val database = DatabaseModule.provideDatabase(context)
            val photoDao = DatabaseModule.providePhotoDao(database)
            val photoRepository = DatabaseModule.providePhotoRepository(photoDao)
            val listDao = DatabaseModule.provideListDao(database)
            val listRepository = DatabaseModule.provideListRepository(listDao, photoDao, database)
            val viewModel = remember { PhotoGalleryViewModel(photoRepository, listRepository) }

            PhotoGalleryScreen(
                viewModel = viewModel,
                onCaptureClick = {
                    navController.navigate(Destination.CameraPermission.route)
                },
                onPhotoClick = { photoId ->
                    navController.navigate(Destination.PhotoReview.createRoute(photoId))
                },
                onListClick = { listId ->
                    navController.navigate(Destination.ListDetail.createRoute(listId))
                },
                onListsOverviewClick = {
                    navController.navigate(Destination.ListsOverview.route)
                }
            )
        }

        composable(Destination.CameraPermission.route) {
            CameraPermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Destination.CameraCapture.route) {
                        popUpTo(Destination.CameraPermission.route) { inclusive = true }
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Destination.CameraCapture.route) {
            val context = LocalContext.current
            val database = DatabaseModule.provideDatabase(context)
            val photoDao = DatabaseModule.providePhotoDao(database)
            val photoRepository = DatabaseModule.providePhotoRepository(photoDao)
            val viewModel = remember { CameraViewModel(photoRepository) }

            CameraCaptureScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onPhotoSaved = { photoId ->
                    navController.popBackStack(Destination.PhotoGallery.route, inclusive = false)
                },
                viewModel = viewModel
            )
        }

        composable(
            route = Destination.PhotoReview.route,
            arguments = listOf(
                navArgument("photoId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
            val context = LocalContext.current
            val database = DatabaseModule.provideDatabase(context)
            val photoDao = DatabaseModule.providePhotoDao(database)
            val photoRepository = DatabaseModule.providePhotoRepository(photoDao)
            val okHttpClient = DatabaseModule.provideOkHttpClient()
            val retrofit = DatabaseModule.provideRetrofit(okHttpClient)
            val cloudVisionApi = DatabaseModule.provideCloudVisionApi(retrofit)
            val cloudVisionService = DatabaseModule.provideCloudVisionService(cloudVisionApi)
            val imageCropService = DatabaseModule.provideImageCropService()
            val textParsingService = DatabaseModule.provideTextParsingService()
            val listCreationService = DatabaseModule.provideListCreationService(database, textParsingService)
            val networkConnectivityService = DatabaseModule.provideNetworkConnectivityService(context)
            val privacyConsentRepository = DatabaseModule.providePrivacyConsentRepository(context)
            val usageTrackingRepository = DatabaseModule.provideUsageTrackingRepository(context)
            val viewModel = remember { PhotoReviewViewModel(photoRepository, cloudVisionService, imageCropService, listCreationService, networkConnectivityService, privacyConsentRepository, usageTrackingRepository, photoId) }

            // Observe crop rect result from RegionSelection screen
            val cropRectJson = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<String>("crop_rect")

            LaunchedEffect(cropRectJson) {
                cropRectJson?.let { json ->
                    val cropRect = json.toCropRect()
                    if (cropRect != null) {
                        viewModel.processCroppedOcr(cropRect)
                    } else {
                        Timber.w("Failed to parse crop rect JSON, falling back to full image OCR")
                        viewModel.processOcr()
                    }
                    // Clear the result after processing
                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("crop_rect")
                }
            }

            PhotoReviewScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onRetakeClick = {
                    navController.navigate(Destination.CameraCapture.route) {
                        popUpTo(Destination.PhotoGallery.route) { inclusive = false }
                    }
                },
                onSelectAreaClick = {
                    navController.navigate(Destination.RegionSelection.createRoute(photoId))
                },
                onDeleteComplete = {
                    navController.popBackStack(Destination.PhotoGallery.route, inclusive = false)
                },
                onListCreated = { listId ->
                    navController.navigate(Destination.ListDetail.createRoute(listId)) {
                        popUpTo(Destination.PhotoReview.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Destination.ListDetail.route,
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getLong("listId") ?: 0L
            val context = LocalContext.current
            val database = DatabaseModule.provideDatabase(context)
            val listDao = DatabaseModule.provideListDao(database)
            val photoDao = DatabaseModule.providePhotoDao(database)
            val listRepository = DatabaseModule.provideListRepository(listDao, photoDao, database)
            val itemDao = DatabaseModule.provideItemDao(database)
            val itemRepository = DatabaseModule.provideItemRepository(itemDao, database)
            val viewModel = remember { ListDetailViewModel(listRepository, itemRepository, listId) }

            ListDetailScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onListDeleted = { navController.popBackStack() }
            )
        }

        composable(Destination.ListsOverview.route) {
            val context = LocalContext.current
            val database = DatabaseModule.provideDatabase(context)
            val listDao = DatabaseModule.provideListDao(database)
            val photoDao = DatabaseModule.providePhotoDao(database)
            val listRepository = DatabaseModule.provideListRepository(listDao, photoDao, database)
            val viewModel = remember { ListsOverviewViewModel(listRepository) }

            ListsOverviewScreen(
                viewModel = viewModel,
                onListClick = { listId ->
                    navController.navigate(Destination.ListDetail.createRoute(listId))
                },
                onCaptureClick = {
                    navController.navigate(Destination.CameraPermission.route)
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Destination.RegionSelection.route,
            arguments = listOf(
                navArgument("photoId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
            val context = LocalContext.current
            val database = DatabaseModule.provideDatabase(context)
            val photoDao = DatabaseModule.providePhotoDao(database)
            val photoRepository = DatabaseModule.providePhotoRepository(photoDao)
            val viewModel = remember { RegionSelectionViewModel(photoRepository, photoId) }

            RegionSelectionScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onScanSelectedArea = { cropRect ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("crop_rect", cropRect.toJson())
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Camera permission screen that handles the permission request flow.
 *
 * Checks permission on entry and on resume. Shows appropriate UI based on state:
 * - If granted, navigates to camera
 * - If denied, shows rationale dialog
 * - If permanently denied, shows denied screen with settings link
 */
@Composable
private fun CameraPermissionScreen(
    onPermissionGranted: () -> Unit,
    onBackClick: () -> Unit,
    viewModel: CameraPermissionViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showRationaleDialog by remember { mutableStateOf(false) }

    // Check permission on resume (handles background revocation)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial permission check
    LaunchedEffect(Unit) {
        if (checkCameraPermission(context)) {
            viewModel.checkPermissionStatus(context)
        }
    }

    // Navigate when permission granted
    LaunchedEffect(uiState) {
        if (uiState is CameraPermissionViewModel.UiState.Granted) {
            onPermissionGranted()
        }
    }

    CameraPermissionHandler(
        onPermissionResult = { granted ->
            val shouldShowRationale = activity?.let { shouldShowCameraRationale(it) } ?: false
            viewModel.onPermissionResult(granted, shouldShowRationale)
        }
    ) { requestPermission ->
        // Request permission on first entry if not already granted
        LaunchedEffect(Unit) {
            if (!checkCameraPermission(context)) {
                requestPermission()
            }
        }

        when (val state = uiState) {
            is CameraPermissionViewModel.UiState.NotRequested -> {
                // Waiting for permission request
            }

            is CameraPermissionViewModel.UiState.Granted -> {
                // Navigation handled by LaunchedEffect above
            }

            is CameraPermissionViewModel.UiState.Denied -> {
                // Show rationale dialog for denied state
                LaunchedEffect(state) {
                    showRationaleDialog = true
                }

                if (showRationaleDialog) {
                    PermissionRationaleDialog(
                        showOpenSettings = false,
                        onTryAgain = {
                            showRationaleDialog = false
                            viewModel.resetToNotRequested()
                            requestPermission()
                        },
                        onOpenSettings = {},
                        onDismiss = {
                            showRationaleDialog = false
                            onBackClick()
                        }
                    )
                }
            }

            is CameraPermissionViewModel.UiState.PermanentlyDenied -> {
                PermissionDeniedScreen(
                    isPermanentlyDenied = true,
                    onTryAgain = {},
                    onOpenSettings = { openAppSettings(context) },
                    onBackClick = onBackClick
                )
            }
        }
    }
}


