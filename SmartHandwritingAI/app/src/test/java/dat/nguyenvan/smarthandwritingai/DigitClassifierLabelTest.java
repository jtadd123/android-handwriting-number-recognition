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
}
