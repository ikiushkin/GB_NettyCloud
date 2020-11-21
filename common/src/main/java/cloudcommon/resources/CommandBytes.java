package cloudcommon.resources;

public enum CommandBytes {
    PACKAGE_START((byte) 15),
    COMMAND_START((byte) 16),
    AUTH((byte) 1),
    AUTH_OK((byte) 2),
    ERROR((byte) 3),
    REG((byte) 4),
    REG_OK((byte) 5),
    FILES_LIST((byte) 6),
    FILES((byte) 7),
    FILE((byte) 8),
    DELETE((byte) 9);

    private byte byteNum;

    CommandBytes(byte byteNum) {
        this.byteNum = byteNum;
    }

    public boolean check(byte b) {
        return b == byteNum;
    }

    public byte getByte() {
        return byteNum;
    }

    @Override
    public String toString() {
        return name();
    }
}
