# List Scanner

An Android application that uses your phone's camera to scan handwritten or printed lists and convert them into editable digital list items using OCR (Optical Character Recognition).

## Features

- **Camera Capture**: Take photos of handwritten or printed lists
- **Region Selection**: Select specific areas of an image to scan (crop before OCR)
- **OCR Processing**: Convert scanned text to digital list items using Google Cloud Vision API
- **List Management**: View, edit, reorder, and manage your scanned lists
- **Photo Gallery**: Browse and manage captured photos
- **Offline Support**: Photos are stored locally; OCR requires network connectivity

## Requirements

- **Android**: API 26 (Android 8.0 Oreo) or higher
- **Target SDK**: 34 (Android 14)
- **Java**: JDK 17
- **Google Cloud Platform account** (for Cloud Vision API)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd list-scanner
```

### 2. Set Up local.properties

The app requires a `local.properties` file in the project root directory. This file is gitignored and contains your local configuration.

1. Copy the example file:
   ```bash
   cp local.properties.example local.properties
   ```

2. Edit `local.properties` and add your configuration:
   ```properties
   # Android SDK path (usually auto-detected by Android Studio)
   sdk.dir=/path/to/your/Android/Sdk

   # Google Cloud Vision API Key (required for OCR)
   CLOUD_VISION_API_KEY=your_api_key_here
   ```

### 3. Set Up Google Cloud Vision API

The app uses Google Cloud Vision API for OCR. Follow these steps to get your API key:

#### Create a Google Cloud Project

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Click **Select a project** → **New Project**
3. Enter a project name (e.g., "List Scanner") and click **Create**
4. Wait for the project to be created, then select it

#### Enable the Cloud Vision API

1. In the Cloud Console, go to **APIs & Services** → **Library**
2. Search for "Cloud Vision API"
3. Click on **Cloud Vision API** in the results
4. Click **Enable**

#### Create an API Key

1. Go to **APIs & Services** → **Credentials**
2. Click **+ CREATE CREDENTIALS** → **API key**
3. Your new API key will be displayed - copy it
4. (Recommended) Click **Edit API key** to add restrictions:
   - Under **API restrictions**, select **Restrict key**
   - Choose **Cloud Vision API** from the list
   - Click **Save**

#### Add the API Key to Your Project

Add your API key to `local.properties`:
```properties
CLOUD_VISION_API_KEY=AIzaSy...your_key_here
```

#### Billing Note

Cloud Vision API requires a billing account. Google Cloud offers:
- **Free tier**: 1,000 units/month for TEXT_DETECTION
- **Pay-as-you-go**: $1.50 per 1,000 units after free tier

See [Cloud Vision Pricing](https://cloud.google.com/vision/pricing) for current rates.

### 4. Build the Application

#### Using Android Studio

1. Open Android Studio
2. Select **File** → **Open** and navigate to the project directory
3. Wait for Gradle sync to complete
4. Click **Run** → **Run 'app'** or press `Shift+F10`

#### Using Command Line

Make sure you have the Android SDK installed and `JAVA_HOME` set to JDK 17.

```bash
# On Linux/macOS with Android Studio's bundled JDK:
export JAVA_HOME=/path/to/android-studio/jbr

# Build debug APK
./gradlew assembleDebug

# The APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

#### Install on Device

```bash
# Install via ADB (device must be connected with USB debugging enabled)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. Run Tests

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "com.listscanner.device.ImageCropServiceTest"
```

## Project Structure

```
app/src/main/kotlin/com/listscanner/
├── data/                    # Database entities, DAOs, repositories
│   ├── dao/                 # Room database access objects
│   ├── entity/              # Database entities (Photo, List, Item)
│   └── repository/          # Repository implementations
├── device/                  # Device services (camera, OCR, network)
│   ├── CloudVisionService   # Google Cloud Vision API integration
│   ├── ImageCropService     # Image cropping utilities
│   └── NetworkConnectivity  # Network state monitoring
├── di/                      # Dependency injection
├── domain/                  # Business logic and interfaces
│   ├── repository/          # Repository interfaces
│   └── service/             # Domain services
└── ui/                      # UI layer (Jetpack Compose)
    ├── components/          # Reusable UI components
    ├── navigation/          # Navigation graph and destinations
    └── screens/             # Screen composables and ViewModels
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Repository pattern
- **Database**: Room
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **Camera**: CameraX
- **Async**: Kotlin Coroutines + Flow
- **Testing**: JUnit 5, MockK, Truth

## Permissions

The app requires the following permissions:

- `CAMERA` - To capture photos of lists
- `INTERNET` - To send images to Cloud Vision API for OCR

## Troubleshooting

### "Invalid API key" error
- Verify your API key is correctly set in `local.properties`
- Ensure the Cloud Vision API is enabled in your GCP project
- Check that billing is enabled on your GCP account

### "API quota exceeded" error
- You've exceeded the free tier limit (1,000 requests/month)
- Check your usage in GCP Console under **APIs & Services** → **Cloud Vision API** → **Metrics**

### Build fails with JDK errors
- Ensure you're using JDK 17
- Set `JAVA_HOME` to point to a JDK 17 installation
- Android Studio's bundled JBR (JetBrains Runtime) works well:
  ```bash
  export JAVA_HOME=/path/to/android-studio/jbr
  ```

### OCR returns no text
- Ensure the image has good lighting and contrast
- Try the region selection feature to focus on specific areas
- Handwriting must be reasonably legible

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
