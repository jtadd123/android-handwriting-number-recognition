package dat.nguyenvan.smarthandwritingai;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DigitClassifierLabelTest {
    @Test
    public void classifierUsesThirtySixDigitAndUppercaseLetterLabels() throws Exception {
        Field numClassesField = DigitClassifier.class.getDeclaredField("NUM_CLASSES");
        numClassesField.setAccessible(true);

        Field labelsField = DigitClassifier.class.getDeclaredField("LABELS");
        labelsField.setAccessible(true);

        String[] expectedLabels = {
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
        };

        assertEquals(36, numClassesField.getInt(null));
        assertArrayEquals(expectedLabels, (String[]) labelsField.get(null));
    }

    @Test
    public void allClassesModePreservesLetterPredictions() {
        float[] probabilities = new float[36];
        probabilities[6] = 0.42f;
        probabilities[13] = 0.99f; // D
        probabilities[14] = 0.81f; // E

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES);

        assertEquals("D", result.label);
        assertEquals(99f, result.confidence, 0.001f);
    }

    @Test
    public void digitLikeThreeCanBeatConfusingLetterE() {
        float[] probabilities = new float[36];
        probabilities[3] = 0.44f;
        probabilities[14] = 0.82f; // E

        float[] pixels = blankGlyph();
        for (int y = 5; y <= 22; y++) {
            int x = y < 14 ? 16 + (y - 5) / 3 : 19 - (y - 14) / 3;
            pixels[y * 28 + x] = 1f;
            pixels[y * 28 + Math.min(27, x + 1)] = 1f;
        }
        for (int x = 10; x <= 20; x++) {
            pixels[5 * 28 + x] = 1f;
            pixels[14 * 28 + x] = 1f;
            pixels[22 * 28 + x] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("3", result.label);
    }

    @Test
    public void digitLikeSixCanBeatConfusingLetterD() {
        float[] probabilities = new float[36];
        probabilities[6] = 0.41f;
        probabilities[13] = 0.90f; // D

        float[] pixels = blankGlyph();
        for (int y = 6; y <= 22; y++) pixels[y * 28 + 9] = 1f;
        for (int x = 9; x <= 19; x++) {
            pixels[14 * 28 + x] = 1f;
            pixels[22 * 28 + x] = 1f;
        }
        for (int y = 14; y <= 22; y++) pixels[y * 28 + 19] = 1f;
        for (int x = 10; x <= 17; x++) pixels[6 * 28 + x] = 1f;

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("6", result.label);
    }

    @Test
    public void digitLikeFourCanBeatConfusingLetterP() {
        float[] probabilities = new float[36];
        probabilities[4] = 0.38f;
        probabilities[25] = 0.84f; // P

        float[] pixels = blankGlyph();
        for (int y = 5; y <= 23; y++) pixels[y * 28 + 18] = 1f;
        for (int x = 8; x <= 21; x++) pixels[14 * 28 + x] = 1f;
        for (int i = 0; i <= 9; i++) pixels[(5 + i) * 28 + (8 + i)] = 1f;

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("4", result.label);
    }

    @Test
    public void digitLikeSevenCanBeatConfusingLetterJ() {
        float[] probabilities = new float[36];
        probabilities[7] = 0.36f;
        probabilities[19] = 0.83f; // J

        float[] pixels = blankGlyph();
        for (int x = 8; x <= 21; x++) pixels[5 * 28 + x] = 1f;
        for (int i = 0; i <= 17; i++) pixels[(6 + i) * 28 + (21 - i / 2)] = 1f;

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("7", result.label);
    }

    @Test
    public void digitLikeTwoCanBeatConfusingLetterF() {
        float[] probabilities = new float[36];
        probabilities[2] = 0.39f;
        probabilities[15] = 0.80f; // F

        float[] pixels = blankGlyph();
        for (int x = 8; x <= 20; x++) {
            pixels[5 * 28 + x] = 1f;
            pixels[22 * 28 + x] = 1f;
        }
        for (int y = 6; y <= 10; y++) pixels[y * 28 + 20] = 1f;
        for (int i = 0; i <= 11; i++) pixels[(10 + i) * 28 + (20 - i)] = 1f;

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("2", result.label);
    }

    @Test
    public void digitLikeFiveCanBeatConfusingLetterJ() {
        float[] probabilities = new float[36];
        probabilities[5] = 0.37f;
        probabilities[19] = 0.81f; // J

        float[] pixels = blankGlyph();
        for (int x = 8; x <= 21; x++) {
            pixels[5 * 28 + x] = 1f;
            pixels[14 * 28 + x] = 1f;
            pixels[22 * 28 + x] = 1f;
        }
        for (int y = 5; y <= 14; y++) pixels[y * 28 + 8] = 1f;
        for (int y = 14; y <= 22; y++) pixels[y * 28 + 21] = 1f;

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("5", result.label);
    }

    @Test
    public void digitLikeEightCanBeatConfusingLetterG() {
        float[] probabilities = new float[36];
        probabilities[8] = 0.31f;
        probabilities[16] = 0.48f; // G
        probabilities[26] = 0.37f; // Q
        probabilities[35] = 0.08f; // Z

        float[] pixels = blankGlyph();
        for (int x = 10; x <= 19; x++) {
            pixels[5 * 28 + x] = 1f;
            pixels[14 * 28 + x] = 1f;
            pixels[22 * 28 + x] = 1f;
        }
        for (int y = 5; y <= 22; y++) {
            pixels[y * 28 + 9] = 1f;
            pixels[y * 28 + 20] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("8", result.label);
    }

    @Test
    public void digitLikeNineCanBeatConfusingThree() {
        float[] probabilities = new float[36];
        probabilities[9] = 0.42f;
        probabilities[3] = 0.82f;
        probabilities[14] = 0.81f; // E

        float[] pixels = blankGlyph();
        for (int x = 10; x <= 19; x++) {
            pixels[5 * 28 + x] = 1f;
            pixels[14 * 28 + x] = 1f;
        }
        for (int y = 5; y <= 14; y++) {
            pixels[y * 28 + 9] = 1f;
            pixels[y * 28 + 20] = 1f;
        }
        for (int y = 14; y <= 23; y++) pixels[y * 28 + 20] = 1f;

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("9", result.label);
    }

    @Test
    public void narrowLoopIsRecognizedAsZero() {
        float[] probabilities = new float[36];
        probabilities[24] = 0.90f; // O (letter)
        probabilities[0] = 0.40f;  // 0 (digit)

        // Draw a narrow loop (width = 10, height = 20) -> ratio = 10.0 / 20.0 = 0.5 < 0.78
        float[] pixels = blankGlyph();
        // Top and bottom bars
        for (int x = 9; x <= 18; x++) {
            pixels[4 * 28 + x] = 1f;
            pixels[23 * 28 + x] = 1f;
        }
        // Left and right vertical lines
        for (int y = 4; y <= 23; y++) {
            pixels[y * 28 + 9] = 1f;
            pixels[y * 28 + 18] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("0", result.label);
    }

    @Test
    public void wideLoopIsRecognizedAsLetterO() {
        float[] probabilities = new float[36];
        probabilities[0] = 0.90f;  // 0 (digit)
        probabilities[24] = 0.40f; // O (letter)

        // Draw a wide/round loop (width = 18, height = 20) -> ratio = 18.0 / 20.0 = 0.9 >= 0.78
        float[] pixels = blankGlyph();
        // Top and bottom bars
        for (int x = 5; x <= 22; x++) {
            pixels[4 * 28 + x] = 1f;
            pixels[23 * 28 + x] = 1f;
        }
        // Left and right vertical lines
        for (int y = 4; y <= 23; y++) {
            pixels[y * 28 + 5] = 1f;
            pixels[y * 28 + 22] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("O", result.label);
    }

    @Test
    public void serifOneIsRecognizedAsOne() {
        float[] probabilities = new float[36];
        probabilities[18] = 0.90f; // I
        probabilities[1] = 0.40f;  // 1

        float[] pixels = blankGlyph();
        // Central vertical stem from y = 4 to y = 23 at x = 14
        for (int y = 4; y <= 23; y++) {
            pixels[y * 28 + 14] = 1f;
        }
        // Top serif pointing down-left: from y = 4 to y = 8, x from 14 down to 10
        pixels[4 * 28 + 14] = 1f;
        pixels[5 * 28 + 13] = 1f;
        pixels[6 * 28 + 12] = 1f;
        pixels[7 * 28 + 11] = 1f;
        pixels[8 * 28 + 10] = 1f;

        // Bottom horizontal bar
        for (int x = 10; x <= 18; x++) {
            pixels[23 * 28 + x] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("1", result.label);
    }

    @Test
    public void nineIsRecognizedInsteadOfConfusedEight() {
        float[] probabilities = new float[36];
        probabilities[8] = 0.90f;  // 8
        probabilities[9] = 0.40f;  // 9

        float[] pixels = blankGlyph();
        // Top loop of 9: closed circle
        for (int x = 10; x <= 19; x++) {
            pixels[5 * 28 + x] = 1f;
            pixels[14 * 28 + x] = 1f;
        }
        for (int y = 5; y <= 14; y++) {
            pixels[y * 28 + 9] = 1f;
            pixels[y * 28 + 20] = 1f;
        }
        // Right tail of 9: goes down on the right side only
        for (int y = 14; y <= 23; y++) {
            pixels[y * 28 + 20] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("9", result.label);
    }

    @Test
    public void letterLIsNotMistakenAsOne() {
        float[] probabilities = new float[36];
        probabilities[21] = 0.90f; // L
        probabilities[1] = 0.40f;  // 1

        float[] pixels = blankGlyph();
        // Spine at x = 9 (from y = 4 to y = 23)
        for (int y = 4; y <= 23; y++) {
            pixels[y * 28 + 9] = 1f;
        }
        // Bottom bar extending to the right: from x = 9 to x = 20 at y = 23
        for (int x = 9; x <= 20; x++) {
            pixels[23 * 28 + x] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("L", result.label);
    }

    @Test
    public void letterEIsNotMistakenAsNine() {
        float[] probabilities = new float[36];
        probabilities[14] = 0.90f; // E
        probabilities[9] = 0.40f;  // 9

        float[] pixels = blankGlyph();
        // Spine at x = 7 (from y = 4 to y = 23)
        for (int y = 4; y <= 23; y++) {
            pixels[y * 28 + 7] = 1f;
        }
        // Top, middle, and bottom horizontal bars
        for (int x = 7; x <= 20; x++) {
            pixels[4 * 28 + x] = 1f;
            pixels[13 * 28 + x] = 1f;
            pixels[23 * 28 + x] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("E", result.label);
    }

    @Test
    public void drawnLWithTopOneIsCorrectedToL() {
        float[] probabilities = new float[36];
        probabilities[1] = 0.90f;  // 1
        probabilities[21] = 0.40f; // L

        float[] pixels = blankGlyph();
        // Spine at x = 9 (from y = 4 to y = 23)
        for (int y = 4; y <= 23; y++) {
            pixels[y * 28 + 9] = 1f;
        }
        // Bottom bar extending to the right: from x = 9 to x = 20 at y = 23
        for (int x = 9; x <= 20; x++) {
            pixels[23 * 28 + x] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("L", result.label);
    }

    @Test
    public void drawnEWithTopNineIsCorrectedToE() {
        float[] probabilities = new float[36];
        probabilities[9] = 0.90f;  // 9
        probabilities[14] = 0.40f; // E

        float[] pixels = blankGlyph();
        // Spine at x = 7 (from y = 4 to y = 23)
        for (int y = 4; y <= 23; y++) {
            pixels[y * 28 + 7] = 1f;
        }
        // Top, middle, and bottom horizontal bars
        for (int x = 7; x <= 20; x++) {
            pixels[4 * 28 + x] = 1f;
            pixels[13 * 28 + x] = 1f;
            pixels[23 * 28 + x] = 1f;
        }

        DigitClassifier.PredictionResult result =
                DigitClassifier.getBestPrediction(probabilities, DigitClassifier.PredictionMode.ALL_CLASSES, pixels);

        assertEquals("E", result.label);
    }

    private static float[] blankGlyph() {
        return new float[28 * 28];
    }
}
