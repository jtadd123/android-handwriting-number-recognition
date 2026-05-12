import numpy as np
import matplotlib.pyplot as plt
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers, models
from tensorflow.keras.datasets import mnist
from tensorflow.keras.utils import to_categorical
import os

print("=" * 60)
print("  SMART HANDWRITING AI - TRAIN MODEL")
print("=" * 60)
print(f"  TensorFlow version: {tf.__version__}")
print(f"  NumPy version: {np.__version__}")
if tf.config.list_physical_devices('GPU'):
    print("  GPU: Co san ✓")
else:
    print("  GPU: Khong co (dung CPU)")
print("=" * 60)


print("\n Dang tai dataset MNIST...")
(X_train, y_train), (X_test, y_test) = mnist.load_data()
print(f"  Training set: {X_train.shape[0]} anh")
print(f"  Test set:     {X_test.shape[0]} anh")

fig, axes = plt.subplots(2, 5, figsize=(12, 5))
fig.suptitle('10 ANH MAU TU DATASET MNIST', fontsize=14, fontweight='bold')
for i, ax in enumerate(axes.flat):
    ax.imshow(X_train[i], cmap='gray')
    ax.set_title(f'Label: {y_train[i]}', fontsize=11)
    ax.axis('off')
plt.tight_layout()
plt.show()

X_train = X_train.reshape(-1, 28, 28, 1).astype('float32') / 255.0
X_test = X_test.reshape(-1, 28, 28, 1).astype('float32') / 255.0
y_train_cat = to_categorical(y_train, 10)
y_test_cat = to_categorical(y_test, 10)
print(f"  X_train shape: {X_train.shape}")
print(f"  X_test shape:  {X_test.shape}")


print("\n Dang xay dung kien truc CNN...")
model = models.Sequential([
    layers.Conv2D(32, (3, 3), activation='relu', input_shape=(28, 28, 1)),
    layers.BatchNormalization(),
    layers.Conv2D(32, (3, 3), activation='relu'),
    layers.BatchNormalization(),
    layers.MaxPooling2D((2, 2)),
    layers.Dropout(0.25),

    layers.Conv2D(64, (3, 3), activation='relu'),
    layers.BatchNormalization(),
    layers.Conv2D(64, (3, 3), activation='relu'),
    layers.BatchNormalization(),
    layers.MaxPooling2D((2, 2)),
    layers.Dropout(0.25),

    layers.Flatten(),
    layers.Dense(256, activation='relu'),
    layers.BatchNormalization(),
    layers.Dropout(0.5),
    layers.Dense(10, activation='softmax')
])
model.summary()


model.compile(
    optimizer=keras.optimizers.Adam(learning_rate=0.001),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

callbacks = [
    keras.callbacks.ReduceLROnPlateau(
        monitor='val_accuracy', factor=0.5, patience=3, min_lr=1e-6, verbose=1
    ),
    keras.callbacks.EarlyStopping(
        monitor='val_accuracy', patience=7, restore_best_weights=True, verbose=1
    )
]

print("Bat dau training...")
history = model.fit(
    X_train, y_train_cat,
    epochs=30, batch_size=128,
    validation_data=(X_test, y_test_cat),
    callbacks=callbacks, verbose=1
)
print("Training hoan tat!")


test_loss, test_accuracy = model.evaluate(X_test, y_test_cat, verbose=0)
print(f"\n  Test Accuracy: {test_accuracy * 100:.2f}%")
print(f"  Test Loss:     {test_loss:.4f}")

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
ax1.plot(history.history['accuracy'], label='Train Accuracy', linewidth=2)
ax1.plot(history.history['val_accuracy'], label='Val Accuracy', linewidth=2)
ax1.set_title('Model Accuracy', fontsize=13, fontweight='bold')
ax1.set_xlabel('Epoch')
ax1.set_ylabel('Accuracy')
ax1.legend(fontsize=11)
ax1.grid(True, alpha=0.3)

ax2.plot(history.history['loss'], label='Train Loss', linewidth=2)
ax2.plot(history.history['val_loss'], label='Val Loss', linewidth=2)
ax2.set_title('Model Loss', fontsize=13, fontweight='bold')
ax2.set_xlabel('Epoch')
ax2.set_ylabel('Loss')
ax2.legend(fontsize=11)
ax2.grid(True, alpha=0.3)
plt.tight_layout()
plt.show()

from sklearn.metrics import confusion_matrix, classification_report
import seaborn as sns

y_pred = model.predict(X_test, verbose=0)
y_pred_classes = np.argmax(y_pred, axis=1)
cm = confusion_matrix(y_test, y_pred_classes)

fig, ax = plt.subplots(figsize=(10, 8))
sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', ax=ax,
            xticklabels=range(10), yticklabels=range(10))
ax.set_title('Confusion Matrix', fontsize=14, fontweight='bold')
ax.set_xlabel('Predicted Label', fontsize=12)
ax.set_ylabel('True Label', fontsize=12)
plt.tight_layout()
plt.show()

print("\nCLASSIFICATION REPORT:")
print(classification_report(y_test, y_pred_classes, digits=4))


indices = np.random.choice(len(X_test), 15, replace=False)
fig, axes = plt.subplots(3, 5, figsize=(15, 9))
fig.suptitle('DU DOAN MAU', fontsize=16, fontweight='bold')
for idx, ax in zip(indices, axes.flat):
    img = X_test[idx]
    prediction = model.predict(img.reshape(1, 28, 28, 1), verbose=0)
    predicted_digit = np.argmax(prediction)
    confidence = np.max(prediction) * 100
    true_label = y_test[idx]
    ax.imshow(img.squeeze(), cmap='gray')
    color = 'green' if predicted_digit == true_label else 'red'
    ax.set_title(f'Pred: {predicted_digit} ({confidence:.1f}%)\nTrue: {true_label}',
                 fontsize=10, color=color, fontweight='bold')
    ax.axis('off')
plt.tight_layout()
plt.show()


print("\n Dang convert model sang TensorFlow Lite...")

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
tflite_model = converter.convert()

with open('model.tflite', 'wb') as f:
    f.write(tflite_model)
file_size_kb = os.path.getsize('model.tflite') / 1024
print(f"  Da luu: model.tflite ({file_size_kb:.1f} KB)")


print("\n Dang kiem tra model TFLite...")
interpreter = tf.lite.Interpreter(model_path='model.tflite')
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"  Input shape: {input_details[0]['shape']}")
print(f"  Input dtype: {input_details[0]['dtype']}")
print(f"  Output shape: {output_details[0]['shape']}")

correct = 0
for i in range(len(X_test)):
    input_data = X_test[i:i+1].astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    output_data = interpreter.get_tensor(output_details[0]['index'])
    if np.argmax(output_data) == y_test[i]:
        correct += 1

tflite_accuracy = correct / len(X_test) * 100
print(f"\n  Keras Accuracy:  {test_accuracy * 100:.2f}%")
print(f"  TFLite Accuracy: {tflite_accuracy:.2f}%")

if tflite_accuracy > 97:
    print("  Model TFLite DAT YEU CAU (> 97%)!")


print("\n HOAN TAT! Dang download file...")
from google.colab import files
files.download('model.tflite')

print("\n" + "=" * 60)
print("  HOAN TAT GIAI DOAN 1!")
print("=" * 60)
print(f"  File: model.tflite ({file_size_kb:.1f} KB)")
print(f"  Accuracy: {tflite_accuracy:.2f}%")
print("  BUOC TIEP THEO:")
print("    1. Lay file model.tflite da download")
print("    2. Copy vao thu muc: app/src/main/assets/")
print("    3. Tiep tuc Giai doan 2: Cau hinh Android Project")
print("=" * 60)
