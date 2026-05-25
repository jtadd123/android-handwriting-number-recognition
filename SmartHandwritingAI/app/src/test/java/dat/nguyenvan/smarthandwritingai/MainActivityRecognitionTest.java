package dat.nguyenvan.smarthandwritingai;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainActivityRecognitionTest {

    @Test
    public void closeFragmentsAreTreatedAsOneGalleryDigit() {
        List<FractionParser.SegmentedSymbol> fragments = Arrays.asList(
                symbol(40, 20, 56, 120),
                symbol(61, 28, 80, 118)
        );

        assertTrue(MainActivity.shouldClassifyWholeImageAsSingleDigit(fragments));
    }

    @Test
    public void separatedCharactersStaySegmented() {
        List<FractionParser.SegmentedSymbol> symbols = Arrays.asList(
                symbol(12, 20, 38, 120),
                symbol(96, 22, 122, 118)
        );

        assertFalse(MainActivity.shouldClassifyWholeImageAsSingleDigit(symbols));
    }

    @Test
    public void croppedSingleCharacterFragmentsAreClassifiedAsOneSymbol() {
        List<FractionParser.SegmentedSymbol> fragments = Arrays.asList(
                symbol(25, 12, 62, 34),
                symbol(48, 30, 74, 92),
                symbol(36, 88, 58, 126)
        );

        assertTrue(MainActivity.shouldClassifyWholeImageAsSingleDigit(fragments));
    }

    private static FractionParser.SegmentedSymbol symbol(float left, float top, float right, float bottom) {
        return new FractionParser.SegmentedSymbol(left, top, right, bottom, null, null);
    }
}
