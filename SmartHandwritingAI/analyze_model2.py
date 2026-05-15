import struct

data = open(r'app\src\main\assets\model.tflite', 'rb').read()

# Parse TFLite flatbuffer manually to get input/output shapes
# TFLite uses FlatBuffers format

# Find output tensor name and shape
# We already know:
# - "output_0" exists in the model
# - "dense" layers exist (conv2d -> dense -> output)
# - Shape [1,36] found 5 times

# Let's try to use flatbuffers package only (without tflite schema)
try:
    import pip._vendor.rich  # just checking pip works
except:
    pass

# Manual approach: search for the output tensor shape
# In a TFLite file, the SubGraph contains Tensors, and each Tensor has a shape
# Let's find the actual output shape by looking near "output_0"

pos = data.find(b'output_0')
print(f"'output_0' found at offset: {pos}")

# Look at surrounding data
context = data[pos-100:pos+100]
# Print as hex with ASCII
for i in range(0, len(context), 16):
    hex_part = ' '.join(f'{b:02x}' for b in context[i:i+16])
    ascii_part = ''.join(chr(b) if 32 <= b < 127 else '.' for b in context[i:i+16])
    print(f"  {i:04x}: {hex_part:48s} {ascii_part}")

# Now let's look for the sequential model name to understand architecture
for keyword in [b'sequential', b'keras', b'serving_default']:
    pos = data.find(keyword)
    if pos >= 0:
        ctx = data[pos:pos+80]
        safe = ''.join(chr(b) if 32 <= b < 127 else '.' for b in ctx)
        print(f"\nFound '{keyword.decode()}' at {pos}: {safe}")

# The key question: does the model output 36 classes (0-9 + A-Z) or something else?
# Let's look for the last dense layer shape which determines output classes
# Search for "dense" near the end of the model (closer to output)
last_dense_pos = data.rfind(b'dense')
if last_dense_pos >= 0:
    context = data[last_dense_pos:last_dense_pos+120]
    safe = ''.join(chr(b) if 32 <= b < 127 else '.' for b in context)
    print(f"\nLast 'dense' at {last_dense_pos}: {safe}")

# Let's also search for specific EMNIST label patterns
# EMNIST ByMerge has 47 classes, ByClass has 62, Letters has 26, Digits has 10, Balanced has 47
# The code currently uses 36 classes (0-9, A-Z)

# Check if there's a label file
import os
assets_dir = r'app\src\main\assets'
for f in os.listdir(assets_dir):
    print(f"\nAsset file: {f} ({os.path.getsize(os.path.join(assets_dir, f))} bytes)")

# Try another approach - use struct to find int vectors that look like tensor shapes
# Tensor shapes in flatbuffers are stored as vectors: [length, dim1, dim2, ...]
# For output [1, N], we'd see: 02 00 00 00 01 00 00 00 XX 00 00 00
for num_classes in [10, 36, 47, 62]:
    pattern = struct.pack('<iii', 2, 1, num_classes)  # vector of 2 ints: [1, num_classes]
    pos = data.find(pattern)
    if pos >= 0:
        print(f"\nFound tensor shape vector [1, {num_classes}] at offset {pos}")
        # Check what's near it
        context = data[max(0,pos-40):pos+40]
        safe = ''.join(chr(b) if 32 <= b < 127 else '.' for b in context)
        print(f"  Context: {safe}")
