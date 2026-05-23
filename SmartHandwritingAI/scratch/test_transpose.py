def transpose(grid):
    # Swap rows and columns
    h = len(grid)
    w = len(grid[0])
    transposed = [[0]*h for _ in range(w)]
    for r in range(h):
        for c in range(w):
            transposed[c][r] = grid[r][c]
    return transposed

def flip_left_right(grid):
    # Flip columns
    flipped = []
    for row in grid:
        flipped.append(row[::-1])
    return flipped

# Represent a vertical line "1" with a small hook at the top-left:
# . . # . .
# . # # . .
# . . # . .
# . . # . .
# . . # . .
grid = [
    [".", ".", "#", ".", "."],
    [".", "#", "#", ".", "."],
    [".", ".", "#", ".", "."],
    [".", ".", "#", ".", "."],
    [".", ".", "#", ".", "."]
]

print("Original Upright Character:")
for row in grid:
    print(" ".join(row))

# fix_emnist_orientation does transpose then flip_left_right
t_grid = transpose(grid)
f_grid = flip_left_right(t_grid)

print("\nAfter Transpose:")
for row in t_grid:
    print(" ".join(row))

print("\nAfter Flip Left-Right (Result of fix_emnist_orientation):")
for row in f_grid:
    print(" ".join(row))
