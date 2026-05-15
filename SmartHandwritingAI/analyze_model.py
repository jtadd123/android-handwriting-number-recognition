import struct

data = open(r'app\src\main\assets\model.tflite', 'rb').read()
print(f"Model file size: {len(data)} bytes")

# Search for the number of output classes as int32 LE
for num in [10, 36, 47, 62]:
    needle = struct.pack('<i', num)
    count = data.count(needle)
    print(f"Number {num} found {count} times as int32")

# Try to find layer names
for keyword in [b'dense', b'softmax', b'output', b'input', b'conv2d', b'flatten']:
    count = data.count(keyword)
    if count > 0:
        pos = data.find(keyword)
        context = data[max(0,pos-20):pos+60]
        safe = ''.join(chr(b) if 32 <= b < 127 else '.' for b in context)
        print(f"Found '{keyword.decode()}' {count} times. Context: {safe}")

# Check for EMNIST vs MNIST indicators
for keyword in [b'emnist', b'EMNIST', b'mnist', b'MNIST']:
    if keyword in data:
        print(f"Found '{keyword.decode()}' in model!")

# Try to find output tensor shape info
# In TFLite, tensor shapes are stored as vectors of int32
# Look for [1, 36] pattern = 01 00 00 00 24 00 00 00
pattern_36 = struct.pack('<ii', 1, 36)
pattern_47 = struct.pack('<ii', 1, 47)
pattern_62 = struct.pack('<ii', 1, 62)
pattern_10 = struct.pack('<ii', 1, 10)

for name, pat in [("[1,10]", pattern_10), ("[1,36]", pattern_36), ("[1,47]", pattern_47), ("[1,62]", pattern_62)]:
    count = data.count(pat)
    if count > 0:
        print(f"Shape pattern {name} found {count} times")

# Try using flatbuffers to parse basic structure
# The TFLite file header
version = struct.unpack('<I', data[4:8])[0]
print(f"TFLite version identifier: {data[4:8]}")

# Try pip install flatbuffers + tflite
try:
    import pip
    pip.main(['install', 'flatbuffers', 'tflite', '-q'])
    from tflite.Model import Model
    import flatbuffers
    buf = bytearray(data)
    model = Model.GetRootAs(buf, 0)
    subgraph = model.Subgraphs(0)
    print(f"\nSubgraph tensors: {subgraph.TensorsLength()}")
    
    # Get input tensor
    inp_idx = subgraph.Inputs(0)
    inp_tensor = subgraph.Tensors(inp_idx)
    inp_shape = [inp_tensor.Shape(i) for i in range(inp_tensor.ShapeLength())]
    print(f"Input tensor shape: {inp_shape}")
    print(f"Input tensor name: {inp_tensor.Name().decode()}")
    
    # Get output tensor
    out_idx = subgraph.Outputs(0)
    out_tensor = subgraph.Tensors(out_idx)
    out_shape = [out_tensor.Shape(i) for i in range(out_tensor.ShapeLength())]
    print(f"Output tensor shape: {out_shape}")
    print(f"Output tensor name: {out_tensor.Name().decode()}")
    
    # Print all tensor names and shapes
    print(f"\nAll tensors:")
    for i in range(subgraph.TensorsLength()):
        t = subgraph.Tensors(i)
        shape = [t.Shape(j) for j in range(t.ShapeLength())]
        name = t.Name().decode() if t.Name() else "unnamed"
        print(f"  [{i}] {name}: {shape}")
        
except Exception as e:
    print(f"Advanced parse failed: {e}")
