# Sheyla_Arteaga_lab15 — Pneumonia Detection from Chest X-Rays

An Android app (Kotlin, Views/XML) that classifies chest X-ray images as
**NORMAL** or **PNEUMONIA** using an on-device TensorFlow Lite model.

It is based on Google's codelab *"Build your first computer-vision app with ML Kit"*
(the "Label Image" screen), but runs a custom model with the **LiteRT / TensorFlow Lite
`Interpreter`** instead of ML Kit, because the model has no metadata.

## Dataset

- **Chest X-Ray Images (Pneumonia)** — Kaggle.
- Two classes: `NORMAL` and `PNEUMONIA`.
- The training data was **balanced to 1341 images per class**.

## Model

- **MobileNetV2** with transfer learning (ImageNet weights), trained in Keras and
  converted to TensorFlow Lite (`model.tflite`).
- **Test accuracy: 0.85**
- Input: `float32 [1, 224, 224, 3]`, RGB pixels in the range **0–255**.
  Normalization is done **inside the model** (Rescaling layer), so the app does not
  normalize pixels.
- Output: `float32 [1, 2]`, softmax probabilities.
- `labels.txt`: index 0 = `NORMAL`, index 1 = `PNEUMONIA`.

## Interface

The UI is built with **Material 3** (Material Components for Android, Views/XML) and
follows the system **light / dark mode**. All content is inside a scroll view, so it also
fits on small screens.

From top to bottom:

1. **Top app bar**: title *Pneumonia Detector*, subtitle *Chest X-ray classifier (TFLite)*.
2. **File name**, e.g. `Image 1/4: IM-0001-0001.jpeg` or `Gallery: my_xray.jpg`.
3. **X-ray card**: the image inside a rounded, elevated card on a black background.
4. **Buttons** with Material icons:

   | Button | Style | What it does |
   |---|---|---|
   | **Previous** | tonal | Shows the previous test image from `assets/` (wraps around). |
   | **Choose image** | tonal | Opens the Android Photo Picker (no permissions needed) to pick any X-ray from the phone. |
   | **Next** | tonal | Shows the next test image from `assets/` (wraps around). |
   | **Label Image** | large, filled | Runs the model on the image currently shown. |

5. **Result card**:
   - Before classifying it shows the hint *"Press Label Image to classify"*.
   - A colored **chip** with the predicted class: green for `NORMAL`, red for `PNEUMONIA`.
   - One **horizontal progress bar per class** with its percentage, highest first.
   - The raw text lines in the same format as the Colab notebook, 4 decimals, highest
     first:
     ```
     PNEUMONIA : 0.9731
     NORMAL : 0.0269
     ```
   - The **inference time** in ms (time spent in `interpreter.run` only).
6. **Disclaimer**: *"For educational purposes only. Not a medical diagnosis."*

Preprocessing in [`MainActivity.kt`](app/src/main/java/com/example/sheyla_arteaga_lab15/MainActivity.kt):

1. Make sure the bitmap is `ARGB_8888`.
2. Resize to 224×224 with bilinear filtering (`createScaledBitmap(..., filter = true)`),
   matching Keras.
3. Write R, G, B (in that order) as floats from 0 to 255 into a direct `ByteBuffer`
   with `ByteOrder.nativeOrder()`.
4. Run `interpreter.run(input, output)` and sort the probabilities.

### Test images (in `app/src/main/assets/`)

| File | Expected class |
|---|---|
| `IM-0001-0001.jpeg` | NORMAL |
| `IM-0003-0001.jpeg` | NORMAL |
| `person100_bacteria_475.jpeg` | PNEUMONIA |
| `person100_bacteria_477.jpeg` | PNEUMONIA |

## Project structure

```
app/src/main/
├── assets/                 # model.tflite, labels.txt and 4 test images
├── java/.../MainActivity.kt          # model loading, preprocessing, UI logic
└── res/
    ├── drawable/ic_*.xml             # Material icons (vector drawables)
    ├── layout/activity_main.xml      # main screen
    ├── layout/item_class_row.xml     # one class row: name, %, progress bar
    ├── values/colors.xml             # class colors (light mode)
    ├── values-night/colors.xml       # class colors (dark mode)
    └── values/strings.xml            # all UI texts
```

Main dependencies: `com.google.ai.edge.litert:litert` (LiteRT, formerly TensorFlow Lite)
and `com.google.android.material:material` (Material 3 components).
`.tflite` files are excluded from APK compression (`androidResources.noCompress`) so the
model can be memory-mapped from assets.

## How to run it in Android Studio

1. Clone the repository:
   ```bash
   git clone https://github.com/shely07-boop/Android-app-classifier-.git
   ```
2. In Android Studio choose **File → Open** and select the project folder.
3. Wait for the Gradle sync to finish (or click **Sync Now** / **File → Sync Project with Gradle Files**).
4. Start an emulator or connect an Android phone (Android 7.0 / API 24 or newer) with USB debugging enabled.
5. Click **Run ▶** (Shift+F10).
6. Use **Previous / Next** to browse the test images and press **Label Image** to classify.
   To try your own X-ray, press **Choose image** (on an emulator, first drag an image file
   onto the emulator window to add it to the gallery).

To build the APK from the command line:

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

> **Disclaimer:** This is an academic lab project. It is not a medical device and must not be
> used for diagnosis.
