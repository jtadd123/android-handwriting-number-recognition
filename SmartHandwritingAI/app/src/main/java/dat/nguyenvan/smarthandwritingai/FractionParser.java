package dat.nguyenvan.smarthandwritingai;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FractionParser {

    public static class SegmentedSymbol {
        public float left;
        public float top;
        public float right;
        public float bottom;
        public Bitmap bitmap;
        public DrawingView.PathData[] paths; // Null for camera/gallery photos
        public boolean isFractionBar = false;

        public SegmentedSymbol(float left, float top, float right, float bottom, Bitmap bitmap, DrawingView.PathData[] paths) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.bitmap = bitmap;
            this.paths = paths;
        }

        public float centerX() { return left + (right - left) / 2f; }
        public float centerY() { return top + (bottom - top) / 2f; }
        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    public static List<DrawActivity.ExpressionToken> parseLayout(
            List<SegmentedSymbol> symbols,
            DigitClassifier classifier,
            boolean isMathMode,
            boolean isFractionMode,
            float brushSize) {

        List<DrawActivity.ExpressionToken> tokens = new ArrayList<>();
        if (symbols == null || symbols.isEmpty()) {
            return tokens;
        }

        if (!isFractionMode) {
            // Sắp xếp các ký tự từ trái sang phải
            Collections.sort(symbols, (s1, s2) -> Float.compare(s1.left, s2.left));
            for (SegmentedSymbol sym : symbols) {
                tokens.add(classifySymbol(sym, classifier, isMathMode, brushSize));
            }
            return tokens;
        }

        // Tìm thanh phân số (Fraction Bar) chính
        SegmentedSymbol fractionBar = null;
        for (SegmentedSymbol sym : symbols) {
            float w = sym.width();
            float h = sym.height();
            float ratio = w / Math.max(1f, h);

            // Thanh phân số phải dẹt ngang (ratio > 2.0f) và có nét vẽ trên/dưới nó
            if (ratio > 2.0f && w > 15f) {
                boolean hasAbove = false;
                boolean hasBelow = false;
                float cy = sym.centerY();
                float pad = Math.max(25f, w * 0.15f);
                float leftBound = sym.left - pad;
                float rightBound = sym.right + pad;

                for (SegmentedSymbol other : symbols) {
                    if (other == sym) continue;
                    float ocx = other.centerX();
                    if (ocx > leftBound && ocx < rightBound) {
                        if (other.centerY() < cy - h * 0.4f) {
                            hasAbove = true;
                        } else if (other.centerY() > cy + h * 0.4f) {
                            hasBelow = true;
                        }
                    }
                }

                if (hasAbove && hasBelow) {
                    fractionBar = sym;
                    sym.isFractionBar = true;
                    break; // Sử dụng thanh phân số đầu tiên tìm thấy
                }
            }
        }

        if (fractionBar == null) {
            // Không thấy phân số, nhận dạng ngang bình thường
            return parseLayout(symbols, classifier, isMathMode, false, brushSize);
        }

        // Tách các ký tự còn lại theo vị trí đối với thanh phân số
        List<SegmentedSymbol> leftSyms = new ArrayList<>();
        List<SegmentedSymbol> rightSyms = new ArrayList<>();
        List<SegmentedSymbol> numSyms = new ArrayList<>();
        List<SegmentedSymbol> denSyms = new ArrayList<>();

        float barLeft = fractionBar.left;
        float barRight = fractionBar.right;
        float barCenterY = fractionBar.centerY();
        float pad = Math.max(25f, fractionBar.width() * 0.12f);

        for (SegmentedSymbol sym : symbols) {
            if (sym == fractionBar) continue;

            float cx = sym.centerX();
            if (cx < barLeft - pad) {
                leftSyms.add(sym);
            } else if (cx > barRight + pad) {
                rightSyms.add(sym);
            } else {
                if (sym.centerY() < barCenterY) {
                    numSyms.add(sym);
                } else {
                    denSyms.add(sym);
                }
            }
        }

        // Đệ quy phân tích các nhánh, ép buộc dùng chế độ toán học (isMathMode = true) cho tử số và mẫu số để tránh đoán sai sang chữ cái (như G, Z)
        List<DrawActivity.ExpressionToken> leftTokens = parseLayout(leftSyms, classifier, isMathMode, isFractionMode, brushSize);
        List<DrawActivity.ExpressionToken> numTokens = parseLayout(numSyms, classifier, true, isFractionMode, brushSize);
        List<DrawActivity.ExpressionToken> denTokens = parseLayout(denSyms, classifier, true, isFractionMode, brushSize);
        List<DrawActivity.ExpressionToken> rightTokens = parseLayout(rightSyms, classifier, isMathMode, isFractionMode, brushSize);

        // Kết hợp: Left + (Num)/(Den) + Right (Chỉ thêm ngoặc đơn nếu có nhiều hơn 1 kí tự)
        tokens.addAll(leftTokens);
        
        boolean numHasParens = numTokens.size() > 1;
        if (numHasParens) {
            tokens.add(new DrawActivity.ExpressionToken("(", true, 100f));
        }
        tokens.addAll(numTokens);
        if (numHasParens) {
            tokens.add(new DrawActivity.ExpressionToken(")", true, 100f));
        }
        
        tokens.add(new DrawActivity.ExpressionToken("/", true, 100f));
        
        boolean denHasParens = denTokens.size() > 1;
        if (denHasParens) {
            tokens.add(new DrawActivity.ExpressionToken("(", true, 100f));
        }
        tokens.addAll(denTokens);
        if (denHasParens) {
            tokens.add(new DrawActivity.ExpressionToken(")", true, 100f));
        }
        
        tokens.addAll(rightTokens);

        return tokens;
    }

    private static DrawActivity.ExpressionToken classifySymbol(
            SegmentedSymbol sym,
            DigitClassifier classifier,
            boolean isMathMode,
            float brushSize) {

        if (sym.isFractionBar) {
            return new DrawActivity.ExpressionToken("/", true, 100f);
        }

        // 1. Nhận dạng bằng Heuristics (Toán tử)
        if (sym.paths != null) {
            // Chế độ vẽ tay: Sử dụng OperatorDetector trực tiếp
            String op = OperatorDetector.detectOperator(sym.paths, brushSize);
            if (op != null) {
                return new DrawActivity.ExpressionToken(op, true, 100f);
            }
        } else {
            // Chế độ chụp/chọn ảnh: Phân tích hình học bitmap để tìm toán tử
            float originalRatio = sym.width() / Math.max(1f, sym.height());
            String op = OperatorDetector.detectOperatorFromBitmap(sym.bitmap, originalRatio);
            if (op != null) {
                return new DrawActivity.ExpressionToken(op, true, 100f);
            }
        }

        // 2. Nhận dạng bằng AI (Mô hình TFLite)
        DigitClassifier.PredictionResult pred = classifier.predict(sym.bitmap, isMathMode, sym.paths == null);
        if (pred != null) {
            String label = pred.label;
            if (label.equalsIgnoreCase("X")) {
                return new DrawActivity.ExpressionToken("*", true, pred.confidence);
            } else if (label.equalsIgnoreCase("D")) {
                return new DrawActivity.ExpressionToken("/", true, pred.confidence);
            } else {
                boolean isDigit = label.length() == 1 && Character.isDigit(label.charAt(0));
                return new DrawActivity.ExpressionToken(label, !isDigit, pred.confidence);
            }
        }

        return new DrawActivity.ExpressionToken("?", false, 0f);
    }
}
