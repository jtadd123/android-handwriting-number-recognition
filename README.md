# ✍️ SmartHandwritingAI

Ứng dụng Android nhận diện chữ viết tay thông minh sử dụng **TensorFlow Lite** và **EMNIST Dataset** — hỗ trợ nhận dạng số (0–9) và chữ cái (A–Z), giải phép tính viết tay, đồng bộ dữ liệu qua **Firebase**.

<p align="center">
  <img src="Images/animation1.jpg" alt="Onboarding 1" width="200">
  <img src="Images/animation2.jpg" alt="Onboarding 2" width="200">
  <img src="Images/animation3.jpg" alt="Onboarding 3" width="200">
</p>

---

## 📖 Tổng Quan

**SmartHandwritingAI** là ứng dụng Android được xây dựng bằng Java, tích hợp mô hình AI (CNN) được huấn luyện trên dataset EMNIST để nhận dạng **36 ký tự** (0–9, A–Z) từ chữ viết tay. Ứng dụng hỗ trợ nhiều phương thức nhập liệu: vẽ tay trực tiếp, chụp ảnh từ camera, hoặc chọn ảnh từ thư viện.

### ✨ Điểm nổi bật

- 🤖 **AI nhận dạng realtime** — Nhận diện ngay khi bạn vẽ xong
- 🧮 **Giải toán AI** — Vẽ biểu thức toán học, AI tự tính kết quả
- 📸 **Camera thông minh** — Chụp ảnh + crop tự động với UCrop
- 🔊 **Text-to-Speech** — Đọc kết quả bằng giọng nói
- 🌙 **Dark Mode** — Giao diện tối hiện đại
- 🌐 **Đa ngôn ngữ** — Tiếng Việt & Tiếng Anh
- ☁️ **Firebase Cloud Sync** — Đồng bộ lịch sử lên đám mây
- 💾 **Room Database** — Lưu trữ lịch sử offline

---

## 📱 Ảnh Giao Diện

### Đăng Nhập & Trang Chủ

<table>
  <tr>
    <td align="center">
      <img src="Images/giaodiendangnhap.jpg" alt="Đăng nhập" width="200"><br>
      <b>Đăng Nhập</b>
    </td>
    <td align="center">
      <img src="Images/giaodiennhandangtiengviet.jpg" alt="Trang chủ Tiếng Việt" width="200"><br>
      <b>Trang Chủ (Tiếng Việt)</b>
    </td>
    <td align="center">
      <img src="Images/giaodiennhandangtienganh.jpg" alt="Trang chủ Tiếng Anh" width="200"><br>
      <b>Trang Chủ (English)</b>
    </td>
  </tr>
</table>

### Vẽ Tay & Nhận Dạng

<table>
  <tr>
    <td align="center">
      <img src="Images/vetay1.jpg" alt="Vẽ tay nhận dạng số" width="200"><br>
      <b>Nhận Dạng Tự Do</b>
    </td>
    <td align="center">
      <img src="Images/vetay2.jpg" alt="Giải toán AI" width="200"><br>
      <b>Giải Toán AI</b>
    </td>
  </tr>
</table>

### Nhận Dạng Từ Ảnh

<table>
  <tr>
    <td align="center">
      <img src="Images/tinhnangnhandang1.jpg" alt="Crop ảnh" width="200"><br>
      <b>Crop & Xoay Ảnh</b>
    </td>
    <td align="center">
      <img src="Images/tinhnangnhandang2.jpg" alt="Kết quả nhận dạng" width="200"><br>
      <b>Kết Quả Nhận Dạng</b>
    </td>
  </tr>
</table>

### Lịch Sử & Cài Đặt

<table>
  <tr>
    <td align="center">
      <img src="Images/lichsu.jpg" alt="Lịch sử dự đoán" width="200"><br>
      <b>Lịch Sử Dự Đoán</b>
    </td>
    <td align="center">
      <img src="Images/caidat1.jpg" alt="Cài đặt - English" width="200"><br>
      <b>Cài Đặt (English)</b>
    </td>
    <td align="center">
      <img src="Images/caidat2.jpg" alt="Cài đặt - Tiếng Việt" width="200"><br>
      <b>Cài Đặt (Tiếng Việt)</b>
    </td>
  </tr>
</table>

---

## 🚀 Chức Năng Chi Tiết

### 1. 🏠 Trang Chủ — Nhận Dạng Từ Ảnh
- Chụp ảnh trực tiếp từ **Camera**
- Chọn ảnh từ **Thư Viện**
- Crop & xoay ảnh tự động bằng **UCrop**
- Hiển thị:
  - Ảnh gốc đã crop
  - **AI Input** — Ảnh 28×28px mà model thực sự nhận
  - Kết quả nhận dạng + độ tự tin
  - **Top 3 dự đoán** với biểu đồ confidence
- Đọc kết quả bằng giọng nói (**TTS**)
- Phản hồi thông minh theo chất lượng ảnh

### 2. ✏️ Vẽ Tay — DrawActivity
- Canvas vẽ tay với tùy chỉnh **màu sắc** và **cỡ nét** bút
- Hai chế độ:
  - **Nhận Dạng Tự Do** — Vẽ từng ký tự, AI nhận diện realtime
  - **Giải Toán AI** — Vẽ biểu thức (VD: `2 + 3 =`), AI phân tách số và toán tử rồi tính kết quả
- Hỗ trợ **Xoay 90°** và **Lật Gương** ảnh vẽ
- Undo / Redo
- Phản hồi AI đánh giá chất lượng chữ viết
- Lưu kết quả vào lịch sử

### 3. 📋 Lịch Sử — HistoryActivity
- Danh sách tất cả kết quả nhận dạng
- **Tìm kiếm** lịch sử
- Lọc theo **Tất cả** / **Yêu thích**
- Đánh dấu ⭐ yêu thích
- Xóa từng mục hoặc xóa tất cả
- Đồng bộ lên Firebase (**Cloud Sync**)

### 4. ⚙️ Cài Đặt — SettingsActivity
| Tính năng | Mô tả |
|---|---|
| **Ngưỡng độ tin cậy** | Slider điều chỉnh ngưỡng confidence (0–100%) |
| **Text-to-Speech** | Bật/tắt đọc kết quả bằng giọng nói |
| **Dark Mode** | Chế độ tối / sáng |
| **Ngôn ngữ** | Chuyển đổi Tiếng Việt ↔ Tiếng Anh |
| **Đồng bộ Firebase** | Sao lưu lịch sử lên Cloud |
| **Xóa dữ liệu** | Xóa toàn bộ lịch sử cục bộ |
| **Tài khoản** | Đăng nhập / Đăng ký / Đăng xuất |

### 5. 🔐 Đăng Nhập — LoginActivity
- Đăng nhập / Đăng ký bằng **Username + Password**
- Hỗ trợ chế độ **Offline** (bỏ qua đăng nhập)
- Lưu trữ tài khoản trên Firebase Realtime Database
- Mã hóa mật khẩu bảo mật

### 6. 🎬 Onboarding
- 3 màn hình giới thiệu tính năng với **Lottie Animation**
- Chỉ hiển thị lần đầu sử dụng

---

## 🧠 Mô Hình AI

### Kiến Trúc CNN

Mô hình được huấn luyện trên **EMNIST Dataset** (digits + letters) với kiến trúc CNN:

```
Input (28×28×1)
   ↓
Conv2D(32) → BatchNorm → Conv2D(32) → BatchNorm → MaxPool → Dropout(0.25)
   ↓
Conv2D(64) → BatchNorm → Conv2D(64) → BatchNorm → MaxPool → Dropout(0.25)
   ↓
Conv2D(128) → BatchNorm → Conv2D(128) → BatchNorm → GlobalAvgPool → Dropout(0.35)
   ↓
Dense(256) → BatchNorm → Dropout(0.5)
   ↓
Dense(36, softmax) → Output
```

### Thông Số

| Thông số | Giá trị |
|---|---|
| **Input** | 28 × 28 × 1 (grayscale) |
| **Output** | 36 classes (0–9, A–Z) |
| **Dataset** | EMNIST Digits + Letters |
| **Optimizer** | Adam (lr=0.001) |
| **Epochs** | 30 (EarlyStopping) |
| **Format** | TensorFlow Lite (float16 quantized) |
| **Kích thước model** | ~655 KB |

### Xử Lý Ảnh Đầu Vào

1. Chuyển ảnh sang **grayscale**
2. Phát hiện & crop vùng chứa ký tự (**bounding box**)
3. Thêm padding để giữ tỷ lệ
4. Resize về **28×28 px**
5. Đảo ngược nền (nền đen, nét trắng — giống EMNIST)
6. Chuẩn hóa pixel [0, 1]

---

## 🏗️ Kiến Trúc Hệ Thống

```
┌─────────────────────────────────────────────────────┐
│                   SmartHandwritingAI                 │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────┐  ┌───────────┐  ┌──────────────────┐  │
│  │  Camera  │  │  Gallery  │  │  Drawing Canvas  │  │
│  └────┬─────┘  └─────┬─────┘  └────────┬─────────┘  │
│       │              │                  │            │
│       └──────────┬───┘                  │            │
│                  ▼                      ▼            │
│         ┌──────────────┐      ┌─────────────────┐   │
│         │  UCrop       │      │  DrawingView    │   │
│         │  (Crop/Rotate│      │  (Canvas)       │   │
│         └──────┬───────┘      └────────┬────────┘   │
│                │                       │            │
│                └───────────┬───────────┘            │
│                            ▼                        │
│                  ┌──────────────────┐               │
│                  │ ImageProcessor   │               │
│                  │ (Preprocessing)  │               │
│                  └────────┬─────────┘               │
│                           ▼                         │
│                  ┌──────────────────┐               │
│                  │ DigitClassifier  │               │
│                  │ (TFLite Model)   │               │
│                  └────────┬─────────┘               │
│                           ▼                         │
│              ┌────────────┴─────────────┐           │
│              ▼                          ▼           │
│    ┌──────────────────┐     ┌────────────────────┐  │
│    │  MathParser      │     │  OperatorDetector  │  │
│    │  FractionParser  │     │  (Math Mode)       │  │
│    └──────────────────┘     └────────────────────┘  │
│                                                     │
│  ┌─────────────────────────────────────────────────┐│
│  │                Data Layer                       ││
│  │  ┌──────────────┐    ┌───────────────────────┐  ││
│  │  │ Room Database │    │ Firebase RTDB         │  ││
│  │  │ (Offline)     │◄──►│ (Cloud Sync)          │  ││
│  │  └──────────────┘    └───────────────────────┘  ││
│  └─────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────┘
```

---

## 🛠️ Công Nghệ Sử Dụng

| Công nghệ | Mục đích |
|---|---|
| **Java** | Ngôn ngữ chính phát triển Android App |
| **TensorFlow Lite** | Inference mô hình AI trên thiết bị |
| **EMNIST Dataset** | Huấn luyện mô hình nhận dạng 36 ký tự |
| **Firebase Realtime Database** | Đồng bộ lịch sử & tài khoản người dùng |
| **Firebase Auth** | Xác thực người dùng |
| **Room Database** | Lưu trữ lịch sử offline cục bộ |
| **UCrop** | Crop & xoay ảnh trước khi nhận dạng |
| **MPAndroidChart** | Biểu đồ Top 3 predictions |
| **Lottie Animation** | Hoạt ảnh Onboarding & Splash |
| **Material Design 3** | Giao diện hiện đại với Dark Mode |
| **Text-to-Speech** | Đọc kết quả bằng giọng nói |
| **TensorFlow / Keras** | Huấn luyện model CNN (Python) |

---

## 📂 Cấu Trúc Dự Án

```
SmartHandwritingAI/
├── app/
│   ├── src/main/
│   │   ├── java/dat/nguyenvan/smarthandwritingai/
│   │   │   ├── SplashActivity.java          # Splash screen
│   │   │   ├── OnboardingActivity.java      # Giới thiệu lần đầu
│   │   │   ├── LoginActivity.java           # Đăng nhập / Đăng ký
│   │   │   ├── MainActivity.java            # Trang chủ (Camera/Gallery)
│   │   │   ├── DrawActivity.java            # Vẽ tay & Giải toán
│   │   │   ├── HistoryActivity.java         # Lịch sử dự đoán
│   │   │   ├── SettingsActivity.java        # Cài đặt ứng dụng
│   │   │   ├── DigitClassifier.java         # TFLite model inference
│   │   │   ├── ImageProcessor.java          # Tiền xử lý ảnh cho AI
│   │   │   ├── DrawingView.java             # Custom View canvas vẽ tay
│   │   │   ├── MathParser.java              # Parser biểu thức toán học
│   │   │   ├── OperatorDetector.java        # Phát hiện toán tử (+, -, ×, ÷, =)
│   │   │   ├── FractionParser.java          # Xử lý phân số
│   │   │   ├── FirebaseSyncHelper.java      # Đồng bộ Firebase
│   │   │   ├── AppDatabase.java             # Room Database config
│   │   │   ├── PredictionEntity.java        # Entity lịch sử dự đoán
│   │   │   ├── PredictionDao.java           # Data Access Object
│   │   │   ├── HistoryAdapter.java          # RecyclerView adapter
│   │   │   ├── OnboardingAdapter.java       # ViewPager adapter
│   │   │   └── UIUtils.java                 # Utility functions
│   │   ├── assets/
│   │   │   └── model.tflite                 # Mô hình AI (~655 KB)
│   │   └── res/
│   │       ├── layout/                      # XML layouts
│   │       ├── values/                      # Strings (Tiếng Việt)
│   │       ├── values-en/                   # Strings (English)
│   │       ├── values-night/                # Dark mode colors
│   │       ├── raw/                         # Lottie animations
│   │       └── drawable/                    # Icons & drawables
│   └── google-services.json                 # Firebase config
├── colab/
│   └── train_mnist_model.py                 # Script train model (Colab)
├── train_emnist_model.py                    # Script train EMNIST 36 classes
├── build.gradle.kts                         # Root Gradle config
└── settings.gradle.kts                      # Gradle settings
```

---

## ⚡ Cài Đặt & Chạy

### Yêu Cầu

- **Android Studio** Ladybug (2024.2) trở lên
- **JDK 11+**
- **Android SDK 36** (compileSdk)
- **Min SDK 24** (Android 7.0+)
- Tài khoản **Firebase** (đã cấu hình Realtime Database & Auth)

### Bước 1: Clone Repository

```bash
git clone https://github.com/jtadd123/android-handwriting-number-recognition.git
cd android-handwriting-number-recognition/SmartHandwritingAI
```

### Bước 2: Cấu hình Firebase

1. Tạo project trên [Firebase Console](https://console.firebase.google.com/)
2. Thêm Android app với package name: `dat.nguyenvan.smarthandwritingai`
3. Tải file `google-services.json` và đặt vào thư mục `app/`
4. Bật **Realtime Database** và **Authentication** trên Firebase Console

### Bước 3: Build & Run

1. Mở thư mục `SmartHandwritingAI` bằng **Android Studio**
2. Đợi Gradle Sync hoàn tất
3. Kết nối thiết bị Android hoặc khởi động Emulator
4. Nhấn ▶️ **Run** để build và cài đặt

---

## 🧪 Huấn Luyện Model

### Chạy trên Google Colab

1. Upload file `colab/train_mnist_model.py` lên Google Colab
2. Chạy toàn bộ script
3. Tải file `model.tflite` về

### Chạy Local

```bash
# Cài đặt thư viện
pip install tensorflow tensorflow-datasets numpy matplotlib scikit-learn seaborn

# Chạy training
python train_emnist_model.py
```

### Sau khi train xong

1. Copy file `model.tflite` vào `SmartHandwritingAI/app/src/main/assets/`
2. Đảm bảo `NUM_CLASSES = 36` trong `DigitClassifier.java`
3. Labels theo thứ tự: `0, 1, 2, ..., 9, A, B, C, ..., Z`

---

## 📊 Cấu Trúc Firebase Realtime Database

```
smarthandwriting/
├── users/
│   └── {username}/
│       ├── email: "..."
│       ├── password: "..." (hashed)
│       └── predictions/
│           └── {prediction_id}/
│               ├── result: "9"
│               ├── confidence: 96.8
│               ├── timestamp: 1716...
│               ├── imageBase64: "..."
│               └── isFavorite: true
```

---

## 🔧 Cấu Hình Gradle

```kotlin
// app/build.gradle.kts
android {
    namespace = "dat.nguyenvan.smarthandwritingai"
    compileSdk = 36
    minSdk = 24
    targetSdk = 36
}

dependencies {
    // TensorFlow Lite
    implementation(libs.tflite)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)

    // Room Database
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // UI Libraries
    implementation(libs.mpandroidchart)  // Biểu đồ
    implementation(libs.lottie)          // Animation
    implementation(libs.ucrop)           // Crop ảnh
    implementation(libs.material)        // Material Design
}
```

---

## 📝 Ghi Chú

- Ứng dụng hoạt động **hoàn toàn offline** — không cần internet để nhận dạng chữ viết tay
- Firebase chỉ cần thiết cho tính năng **đồng bộ Cloud** và **đăng nhập**
- Model TFLite chạy **on-device**, đảm bảo tốc độ và quyền riêng tư
- Hỗ trợ **Dark Mode** tự động theo theme hệ thống
- Đa ngôn ngữ: Tiếng Việt (mặc định) & Tiếng Anh

---

## 🗂️ Trạng Thái Project

| Module | Trạng thái |
|---|---|
| Mô hình AI (TFLite) | ✅ Hoàn thành |
| Nhận dạng từ Camera/Gallery | ✅ Hoàn thành |
| Vẽ tay & nhận dạng realtime | ✅ Hoàn thành |
| Giải toán AI | ✅ Hoàn thành |
| Lịch sử dự đoán (Room DB) | ✅ Hoàn thành |
| Firebase Cloud Sync | ✅ Hoàn thành |
| Đăng nhập / Đăng ký | ✅ Hoàn thành |
| Dark Mode & Đa ngôn ngữ | ✅ Hoàn thành |
| Onboarding & Animations | ✅ Hoàn thành |
| Text-to-Speech | ✅ Hoàn thành |

---

## 👨‍💻 Tác Giả

**Nguyễn Văn Đạt**

---

## 📄 License

Dự án này được phát triển cho mục đích học tập và nghiên cứu.
