import os
import random

import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf
import tensorflow_datasets as tfds
from sklearn.metrics import classification_report, confusion_matrix
from tensorflow import keras
from tensorflow.keras import layers, models

try:
    import seaborn as sns
except ImportError:
    sns = None


print("=" * 70)
print("  SMART HANDWRITING AI - TRAIN EMNIST MODEL 0-9 + A-Z")
print("=" * 70)
print(f"  TensorFlow version: {tf.__version__}")
print(f"  NumPy version: {np.__version__}")
if tf.config.list_physical_devices("GPU"):
    print("  GPU: Co san")
else:
    print("  GPU: Khong co (dung CPU)")
print("=" * 70)


# ===== CAU HINH =====
SEED = 42
IMG_SIZE = 28
NUM_CLASSES = 36
BATCH_SIZE = 256
EPOCHS = 30
VALIDATION_SIZE = 20000
AUTOTUNE = tf.data.AUTOTUNE
CLASS_NAMES = list("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ")

random.seed(SEED)
np.random.seed(SEED)
tf.random.set_seed(SEED)


# ===== TAI VA CHUAN BI DATASET EMNIST =====
print("\nDang tai dataset EMNIST...")
print("  - emnist/digits  -> labels 0-9")
print("  - emnist/letters -> labels A-Z")

(digits_train, digits_test), digits_info = tfds.load(
    "emnist/digits",
    split=["train", "test"],
    as_supervised=True,
    with_info=True,
    shuffle_files=True,
)

(letters_train, letters_test), letters_info = tfds.load(
    "emnist/letters",
    split=["train", "test"],
    as_supervised=True,
    with_info=True,
    shuffle_files=True,
)

print(f"  Digits train:  {digits_info.splits['train'].num_examples} anh")
print(f"  Digits test:   {digits_info.splits['test'].num_examples} anh")
print(f"  Letters train: {letters_info.splits['train'].num_examples} anh")
print(f"  Letters test:  {letters_info.splits['test'].num_examples} anh")


def fix_emnist_orientation(image):
    image = tf.transpose(image, perm=[1, 0, 2])
    image = tf.image.flip_left_right(image)
    return image


def preprocess_digit(image, label):
    image = fix_emnist_orientation(image)
    image = tf.cast(image, tf.float32) / 255.0
    label = tf.cast(label, tf.int32)
    return image, label


def preprocess_letter(image, label):
    image = fix_emnist_orientation(image)
    image = tf.cast(image, tf.float32) / 255.0
    # EMNIST letters label goc: 1=A, 2=B, ..., 26=Z
    # Label moi cho app: 10=A, 11=B, ..., 35=Z
    label = tf.cast(label, tf.int32) + 9
    return image, label


digits_train = digits_train.map(preprocess_digit, num_parallel_calls=AUTOTUNE)
digits_test = digits_test.map(preprocess_digit, num_parallel_calls=AUTOTUNE)
letters_train = letters_train.map(preprocess_letter, num_parallel_calls=AUTOTUNE)
letters_test = letters_test.map(preprocess_letter, num_parallel_calls=AUTOTUNE)

train_all = digits_train.concatenate(letters_train)
test_all = digits_test.concatenate(letters_test)

train_all = train_all.shuffle(100000, seed=SEED, reshuffle_each_iteration=False)
val_ds = train_all.take(VALIDATION_SIZE)
train_ds = train_all.skip(VALIDATION_SIZE)

train_ds = train_ds.shuffle(100000, seed=SEED, reshuffle_each_iteration=True)
train_ds = train_ds.batch(BATCH_SIZE).prefetch(AUTOTUNE)
val_ds = val_ds.batch(BATCH_SIZE).cache().prefetch(AUTOTUNE)
test_ds = test_all.batch(BATCH_SIZE).cache().prefetch(AUTOTUNE)

print(f"  So lop: {NUM_CLASSES}")
print(f"  Labels: {CLASS_NAMES}")
print(f"  Validation set: {VALIDATION_SIZE} anh")


# ===== HIEN THI ANH MAU =====
sample_ds = train_all.take(20)
fig, axes = plt.subplots(4, 5, figsize=(12, 8))
fig.suptitle("20 ANH MAU TU DATASET EMNIST", fontsize=14, fontweight="bold")
for (image, label), ax in zip(sample_ds, axes.flat):
    ax.imshow(tf.squeeze(image), cmap="gray")
    ax.set_title(f"Label: {CLASS_NAMES[int(label.numpy())]}", fontsize=11)
    ax.axis("off")
plt.tight_layout()
plt.show()


# ===== XAY DUNG KIEN TRUC CNN =====
print("\nDang xay dung kien truc CNN...")
model = models.Sequential(
    [
        layers.Input(shape=(IMG_SIZE, IMG_SIZE, 1)),
        layers.Conv2D(32, (3, 3), padding="same", activation="relu"),
        layers.BatchNormalization(),
        layers.Conv2D(32, (3, 3), padding="same", activation="relu"),
        layers.BatchNormalization(),
        layers.MaxPooling2D((2, 2)),
        layers.Dropout(0.25),
        layers.Conv2D(64, (3, 3), padding="same", activation="relu"),
        layers.BatchNormalization(),
        layers.Conv2D(64, (3, 3), padding="same", activation="relu"),
        layers.BatchNormalization(),
        layers.MaxPooling2D((2, 2)),
        layers.Dropout(0.25),
        layers.Conv2D(128, (3, 3), padding="same", activation="relu"),
        layers.BatchNormalization(),
        layers.Conv2D(128, (3, 3), padding="same", activation="relu"),
        layers.BatchNormalization(),
        layers.GlobalAveragePooling2D(),
        layers.Dropout(0.35),
        layers.Dense(256, activation="relu"),
        layers.BatchNormalization(),
        layers.Dropout(0.5),
        layers.Dense(NUM_CLASSES, activation="softmax"),
    ]
)
model.summary()


# ===== COMPILE VA TRAIN MODEL =====
model.compile(
    optimizer=keras.optimizers.Adam(learning_rate=0.001),
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"],
)

# Digits co nhieu mau hon letters, nen tang trong so cho letters de model can bang hon.
digit_train_count = digits_info.splits["train"].num_examples
letter_train_count = letters_info.splits["train"].num_examples
digit_count_per_class = digit_train_count / 10
letter_count_per_class = letter_train_count / 26
total_train_count = digit_train_count + letter_train_count

class_weight = {}
for class_id in range(NUM_CLASSES):
    count = digit_count_per_class if class_id < 10 else letter_count_per_class
    class_weight[class_id] = total_train_count / (NUM_CLASSES * count)

callbacks = [
    keras.callbacks.ModelCheckpoint(
        "emnist_36_cnn_best.keras",
        monitor="val_accuracy",
        mode="max",
        save_best_only=True,
        verbose=1,
    ),
    keras.callbacks.ReduceLROnPlateau(
        monitor="val_accuracy",
        factor=0.5,
        patience=3,
        min_lr=1e-6,
        verbose=1,
    ),
    keras.callbacks.EarlyStopping(
        monitor="val_accuracy",
        patience=7,
        restore_best_weights=True,
        verbose=1,
    ),
]

print("\nBat dau training...")
history = model.fit(
    train_ds,
    epochs=EPOCHS,
    validation_data=val_ds,
    callbacks=callbacks,
    class_weight=class_weight,
    verbose=1,
)
print("Training hoan tat!")


# ===== DANH GIA MODEL =====
test_loss, test_accuracy = model.evaluate(test_ds, verbose=0)
print(f"\n  Test Accuracy: {test_accuracy * 100:.2f}%")
print(f"  Test Loss:     {test_loss:.4f}")

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
ax1.plot(history.history["accuracy"], label="Train Accuracy", linewidth=2)
ax1.plot(history.history["val_accuracy"], label="Val Accuracy", linewidth=2)
ax1.set_title("Model Accuracy", fontsize=13, fontweight="bold")
ax1.set_xlabel("Epoch")
ax1.set_ylabel("Accuracy")
ax1.legend(fontsize=11)
ax1.grid(True, alpha=0.3)

ax2.plot(history.history["loss"], label="Train Loss", linewidth=2)
ax2.plot(history.history["val_loss"], label="Val Loss", linewidth=2)
ax2.set_title("Model Loss", fontsize=13, fontweight="bold")
ax2.set_xlabel("Epoch")
ax2.set_ylabel("Loss")
ax2.legend(fontsize=11)
ax2.grid(True, alpha=0.3)
plt.tight_layout()
plt.show()


# ===== CONFUSION MATRIX VA CLASSIFICATION REPORT =====
y_true = []
y_pred_classes = []
for images, labels in test_ds:
    predictions = model.predict(images, verbose=0)
    y_pred_classes.extend(np.argmax(predictions, axis=1).tolist())
    y_true.extend(labels.numpy().tolist())

cm = confusion_matrix(y_true, y_pred_classes)

fig, ax = plt.subplots(figsize=(14, 12))
if sns is not None:
    sns.heatmap(
        cm,
        annot=False,
        fmt="d",
        cmap="Blues",
        ax=ax,
        xticklabels=CLASS_NAMES,
        yticklabels=CLASS_NAMES,
    )
else:
    ax.imshow(cm, interpolation="nearest", cmap="Blues")
    ax.set_xticks(np.arange(NUM_CLASSES), CLASS_NAMES, rotation=90)
    ax.set_yticks(np.arange(NUM_CLASSES), CLASS_NAMES)

ax.set_title("Confusion Matrix - EMNIST 0-9 + A-Z", fontsize=14, fontweight="bold")
ax.set_xlabel("Predicted Label", fontsize=12)
ax.set_ylabel("True Label", fontsize=12)
plt.tight_layout()
plt.show()

print("\nCLASSIFICATION REPORT:")
print(classification_report(y_true, y_pred_classes, target_names=CLASS_NAMES, digits=4))


# ===== DU DOAN MAU =====
sample_images, sample_labels = next(iter(test_ds.unbatch().shuffle(5000, seed=SEED).batch(15)))
sample_pred = model.predict(sample_images, verbose=0)

fig, axes = plt.subplots(3, 5, figsize=(15, 9))
fig.suptitle("DU DOAN MAU", fontsize=16, fontweight="bold")
for img, label, prediction, ax in zip(sample_images, sample_labels, sample_pred, axes.flat):
    predicted_id = int(np.argmax(prediction))
    confidence = float(np.max(prediction)) * 100
    true_id = int(label.numpy())
    ax.imshow(tf.squeeze(img), cmap="gray")
    color = "green" if predicted_id == true_id else "red"
    ax.set_title(
        f"Pred: {CLASS_NAMES[predicted_id]} ({confidence:.1f}%)\nTrue: {CLASS_NAMES[true_id]}",
        fontsize=10,
        color=color,
        fontweight="bold",
    )
    ax.axis("off")
plt.tight_layout()
plt.show()


# ===== LUU KERAS MODEL VA LABELS =====
model.save("emnist_36_cnn.keras")
with open("labels.txt", "w", encoding="utf-8") as f:
    for label in CLASS_NAMES:
        f.write(label + "\n")
print("\nDa luu: emnist_36_cnn.keras")
print("Da luu: labels.txt")


# ===== CONVERT SANG TENSORFLOW LITE =====
print("\nDang convert model sang TensorFlow Lite...")

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_model = converter.convert()

with open("model.tflite", "wb") as f:
    f.write(tflite_model)
file_size_kb = os.path.getsize("model.tflite") / 1024
print(f"  Da luu: model.tflite ({file_size_kb:.1f} KB)")


# ===== KIEM TRA MODEL TFLITE =====
print("\nDang kiem tra model TFLite...")
interpreter = tf.lite.Interpreter(model_path="model.tflite")
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"  Input shape: {input_details[0]['shape']}")
print(f"  Input dtype: {input_details[0]['dtype']}")
print(f"  Output shape: {output_details[0]['shape']}")

correct = 0
total = 0
for images, labels in test_ds:
    batch = images.numpy().astype(np.float32)
    label_batch = labels.numpy()
    for i in range(batch.shape[0]):
        interpreter.set_tensor(input_details[0]["index"], batch[i : i + 1])
        interpreter.invoke()
        output_data = interpreter.get_tensor(output_details[0]["index"])
        if int(np.argmax(output_data)) == int(label_batch[i]):
            correct += 1
        total += 1

tflite_accuracy = correct / total * 100
print(f"\n  Keras Accuracy:  {test_accuracy * 100:.2f}%")
print(f"  TFLite Accuracy: {tflite_accuracy:.2f}%")

if tflite_accuracy > 85:
    print("  Model TFLite DAT YEU CAU CO BAN!")


# ===== COPY MODEL VAO ANDROID PROJECT NEU CHAY O ROOT REPO =====
android_assets_dir = "SmartHandwritingAI/app/src/main/assets"
android_model_path = os.path.join(android_assets_dir, "model.tflite")
if os.path.isdir(android_assets_dir):
    import shutil

    shutil.copyfile("model.tflite", android_model_path)
    print(f"\nDa copy model vao Android project: {android_model_path}")


# ===== DOWNLOAD FILE NEU CHAY TREN GOOGLE COLAB =====
try:
    from google.colab import files

    print("\nDang download file model.tflite...")
    files.download("model.tflite")
    files.download("labels.txt")
except ImportError:
    print("\nKhong phai Google Colab, bo qua buoc download.")


print("\n" + "=" * 70)
print("  HOAN TAT TRAIN MODEL 0-9 + A-Z!")
print("=" * 70)
print(f"  File: model.tflite ({file_size_kb:.1f} KB)")
print(f"  Labels: labels.txt ({NUM_CLASSES} labels)")
print(f"  Accuracy: {tflite_accuracy:.2f}%")
print("  BUOC TIEP THEO:")
print("    1. Copy model.tflite vao: SmartHandwritingAI/app/src/main/assets/")
print("    2. Dam bao Android DigitClassifier.java dang NUM_CLASSES = 36")
print("    3. LABELS phai theo thu tu: 0-9, A-Z")
print("=" * 70)
