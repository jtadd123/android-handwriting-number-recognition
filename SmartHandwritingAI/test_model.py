import numpy as np
import tensorflow as tf

# Load the TFLite model
model_path = 'app/src/main/assets/model.tflite'
interpreter = tf.lite.Interpreter(model_path=model_path)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print("Input shape:", input_details[0]['shape'])
print("Output shape:", output_details[0]['shape'])

LABELS = [
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
]

def predict_bitmap(img_28x28):
    # Normalize to [0, 1]
    input_data = img_28x28.reshape(1, 28, 28, 1).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    output_data = interpreter.get_tensor(output_details[0]['index'])[0]
    
    # Get top 3 predictions
    top_indices = np.argsort(output_data)[::-1][:3]
    results = []
    for idx in top_indices:
        results.append((LABELS[idx], output_data[idx] * 100))
    return results

# Let's create a synthesized digit '1' (a vertical line down the center)
img_1 = np.zeros((28, 28), dtype=np.float32)
img_1[4:24, 14] = 1.0
img_1[4:24, 13] = 0.8
img_1[4:24, 15] = 0.8

# Let's create a synthesized digit '0' (a simple oval)
img_0 = np.zeros((28, 28), dtype=np.float32)
for y in range(28):
    for x in range(28):
        # Oval equation: ((x-14)/8)^2 + ((y-14)/10)^2 approx 1
        dist = ((x-14)/6.0)**2 + ((y-14)/9.0)**2
        if 0.7 < dist < 1.3:
            img_0[y, x] = 1.0

# Print predictions for standard, transposed, and rotated versions
print("\n--- Testing Synthesized '1' ---")
print("Normal vertical line:", predict_bitmap(img_1))
print("Transposed line:", predict_bitmap(img_1.T))
print("Flipped line (L-R):", predict_bitmap(np.fliplr(img_1)))

print("\n--- Testing Synthesized '0' ---")
print("Normal oval:", predict_bitmap(img_0))
print("Transposed oval:", predict_bitmap(img_0.T))

# Let's also test a typical '1' with a hook at the top and a bar at the bottom
# Draw:
# Hook: (11,6) to (14,4)
# Stem: (14,4) to (14,24)
# Base: (10,24) to (18,24)
img_1_hook = np.zeros((28, 28), dtype=np.float32)
# Stem
img_1_hook[4:24, 14] = 1.0
# Hook
img_1_hook[6, 12] = 0.7
img_1_hook[5, 13] = 0.9
# Base
img_1_hook[23, 10:19] = 1.0

print("\n--- Testing Synthesized '1' with hook & base ---")
print("Normal hook-1:", predict_bitmap(img_1_hook))
print("Transposed hook-1:", predict_bitmap(img_1_hook.T))
print("Flipped hook-1 (L-R):", predict_bitmap(np.fliplr(img_1_hook)))
