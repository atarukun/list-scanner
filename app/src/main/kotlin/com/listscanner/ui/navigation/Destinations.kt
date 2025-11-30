package com.listscanner.ui.navigation

/**
 * Type-safe navigation destinations for the app.
 *
 * Each destination defines a unique route string used by Navigation Compose.
 */
sealed class Destination(val route: String) {
    /** App home screen showing photo gallery */
    data object PhotoGallery : Destination("photo_gallery")

    /** Camera permission handling screen */
    data object CameraPermission : Destination("camera_permission")

    /** Camera capture screen for taking photos */
    data object CameraCapture : Destination("camera_capture")

    /** Photo review screen for viewing and processing a photo */
    data object PhotoReview : Destination("photo_review/{photoId}") {
        fun createRoute(photoId: Long) = "photo_review/$photoId"
    }

    /** List detail screen for viewing and editing a shopping list */
    data object ListDetail : Destination("list_detail/{listId}") {
        fun createRoute(listId: Long) = "list_detail/$listId"
    }

    /** Lists overview screen showing all shopping lists */
    data object ListsOverview : Destination("lists_overview")

    /** Region selection screen for cropping photo before OCR */
    data object RegionSelection : Destination("region_selection/{photoId}") {
        fun createRoute(photoId: Long) = "region_selection/$photoId"
    }
}
