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

    private static float[] blankGlyph() {
        return new float[28 * 28];
    }
}
