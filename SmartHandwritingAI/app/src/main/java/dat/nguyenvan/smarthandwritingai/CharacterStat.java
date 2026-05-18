package dat.nguyenvan.smarthandwritingai;

/**
 * POJO for character statistics query result.
 * Used by PredictionDao.getCharacterStats().
 */
public class CharacterStat {
    public String result;
    public int count;
    public float avgConf;
}
