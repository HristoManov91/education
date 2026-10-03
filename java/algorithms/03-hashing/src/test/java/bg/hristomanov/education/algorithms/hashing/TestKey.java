package bg.hristomanov.education.algorithms.hashing;

/**
 * Test key с контролиран hashCode, за да можем детерминистично да създаваме collisions.
 */
record TestKey(String value, int forcedHash) {

    @Override
    public int hashCode() {
        return forcedHash;
    }
}
