package cloudcommon.services.transfer;

import io.netty.buffer.ByteBuf;
import cloudcommon.services.LogServiceCommon;
import cloudcommon.settings.GlobalSettings;

import java.nio.ByteBuffer;

public class CommandPackage {
    byte[] bytes;
    private byte command;
    private ByteBuf byteBuf;

    public CommandPackage(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    public void load() {
        command = byteBuf.readByte();
        bytes = new byte[GlobalSettings.COMMAND_DATA_LENGTH];
        byteBuf.readBytes(bytes);
//        System.out.println("in: " + CommandBytes.getCommand(command) + ": " + Arrays.toString(bytes));
    }

    public byte getByteCommandData(int i) {
        if (i < 0 || i > GlobalSettings.COMMAND_DATA_LENGTH) {
            LogServiceCommon.APP.error("Illegal argument in getByte of CommandPackage - " + i);
            throw new IllegalArgumentException();
        }
        return bytes[i];
    }

    public int getIntCommandData() {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public byte getCommand() {
        return command;
    }
}
