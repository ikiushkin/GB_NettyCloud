package cloudcommon.services.transfer;

import io.netty.buffer.ByteBuf;
import cloudcommon.callbacks.MessageCallback;
import cloudcommon.exceptions.NoEnoughDataException;
import cloudcommon.resources.FileRepresentation;
import cloudcommon.services.LogServiceCommon;
import cloudcommon.settings.GlobalSettings;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

// TODO: 14.02.2020 прикрутить логирование в клиент

public class FileDownloader {
    private final String FILENAME_PATTERN = "^[.\\w\\p{L}~@#$%^\\-_(){}'` ]+$";

    private final int BUFFER_SIZE;
    private Path rootDir;
    private ByteBuf byteBuf;
    private int filenameLen;
    private String filename;
    private long fileDate;
    private long fileLen;
    private Path file;
    private byte[] checksum;
    private State state;
    private FileOutputStream out;
    private MessageDigest md;
    private boolean fileInfoOnly;
    private byte[] readBytes;

    private Progress progress;
    private MessageCallback messageCallback;

    public FileDownloader(Path rootDir, ByteBuf byteBuf, int BUFFER_SIZE, Progress progress) {
        this(rootDir, byteBuf, BUFFER_SIZE);
        this.progress = progress;
    }

    public FileDownloader(Path rootDir, ByteBuf byteBuf, int BUFFER_SIZE) {
        this.BUFFER_SIZE = BUFFER_SIZE;
        this.rootDir = rootDir;
        this.byteBuf = byteBuf;
        this.fileInfoOnly = false;
        this.state = State.FILENAME_LENGTH;
        this.readBytes = new byte[BUFFER_SIZE];
        startChecksumCounter();
    }

    public static void checkAvailableData(ByteBuf buf, int length) throws NoEnoughDataException {
        if (buf.readableBytes() < length) throw new NoEnoughDataException();
    }

    public int download() throws NoEnoughDataException {
        try {
            if (state == State.FILENAME_LENGTH) readFilenameLen();
            if (state == State.FILE_INFO) readFilename();
            if (state == State.FILE_DATA) downloadFileData();
            if (state == State.CHECKSUM) readChecksum();
        } catch (IOException e) {
            LogServiceCommon.TRANSFER.error("Error during file download", e.toString());
            LogServiceCommon.TRANSFER.error(e);
            messageCallback("Error during file download");
            closeFileForWrite();
            state = State.FAIL;
        }
        return getErrorCode();
    }

    private int getErrorCode() {
        if (state == State.SUCCESS) return 1;
        else if (state == State.FAIL) return -1;
        else return 0;
    }

    // TODO: 26.02.2020 надо переписать протокол на возможность отправки только имени без дополнительной информации
    public String downloadFileName() throws NoEnoughDataException {
        if (downloadFileInfo()) return filename;
        else return null;
    }

    public FileRepresentation downloadFileRepresentation() throws NoEnoughDataException {
        if (downloadFileInfo()) return new FileRepresentation(filename, fileLen, fileDate);
        else return null;
    }

    private boolean downloadFileInfo() throws NoEnoughDataException {
        fileInfoOnly = true;
        if (download() == 1) {
            fileInfoOnly = false;
            return true;
        } else return false;
    }

    private void readFilenameLen() throws NoEnoughDataException {
        checkAvailableData(byteBuf, Short.BYTES);
        filenameLen = byteBuf.readShort();
        if (filenameLen <= 0) {
            LogServiceCommon.TRANSFER.error("Filename length <= 0");
            state = State.FAIL;
        } else state = State.FILE_INFO;
        LogServiceCommon.TRANSFER.info("Checked filename length - " + filenameLen);
    }

    private void readFilename() throws NoEnoughDataException, IOException {
        checkAvailableData(byteBuf, filenameLen + 2 * Long.BYTES);
        filename = byteBuf.readCharSequence(filenameLen, StandardCharsets.UTF_8).toString();
        if (!filename.matches(FILENAME_PATTERN)) {
            LogServiceCommon.TRANSFER.error("Filename doesn't match pattern - " + filename);
            state = State.FAIL;
            return;
        }
        file = rootDir.resolve(filename);
        fileLen = byteBuf.readLong();
        if (fileLen < 0) {
            LogServiceCommon.TRANSFER.error("File length < 0");
            state = State.FAIL;
            return;
        }
        fileDate = byteBuf.readLong();
        LogServiceCommon.TRANSFER.info("filename - " + filename, "file length - " + fileLen, "file date - " + fileDate);
        if (fileInfoOnly) {
            LogServiceCommon.TRANSFER.info("File info getting success");
            state = State.SUCCESS;
        } else {
            if (progress != null) progress.setMaxValue(fileLen);
            state = State.FILE_DATA;
            LogServiceCommon.TRANSFER.info("Downloading file - " + filename);
            messageCallback("Downloading file " + filename);
            openFileForWrite();
        }
    }

    private void downloadFileData() throws NoEnoughDataException, IOException {
        while (fileLen > 0) {
            int blockSize = fileLen >= BUFFER_SIZE ? BUFFER_SIZE : (int) fileLen;
            checkAvailableData(byteBuf, 1);
            if (byteBuf.readableBytes() < blockSize) blockSize = byteBuf.readableBytes();
            byteBuf.readBytes(readBytes, 0, blockSize);
            out.write(readBytes, 0, blockSize);
            md.update(readBytes, 0, blockSize);
            fileLen -= blockSize;
            if (progress != null) progress.addProgress(blockSize);
        }
        checksum = md.digest();
        if (progress != null) progress.resetProgress();
        state = State.CHECKSUM;
        closeFileForWrite();
        LogServiceCommon.TRANSFER.info("Download complete - " + filename);
    }

    private void readChecksum() throws NoEnoughDataException, IOException {
        checkAvailableData(byteBuf, GlobalSettings.CHECKSUM_LENGTH);
        for (int i = 0; i < GlobalSettings.CHECKSUM_LENGTH; i++) {
            if (byteBuf.readByte() != checksum[i]) {
                LogServiceCommon.TRANSFER.error("Checksum error. Incoming file checksum - " + Arrays.toString(checksum));
                Files.deleteIfExists(file);
                state = State.FAIL;
                return;
            }
        }
        state = State.SUCCESS;
        messageCallback("Download complete. Checksum OK.");
        LogServiceCommon.TRANSFER.info("Checksum for " + filename + " - OK");
    }

    public void reset() {
        filenameLen = 0;
        fileLen = 0;
        filename = null;
        file = null;
        fileInfoOnly = false;
        state = State.FILENAME_LENGTH;
        readBytes = new byte[BUFFER_SIZE];
        if (progress != null) progress.setMaxValue(0);
        md.reset();
        closeFileForWrite();
    }

    // TODO: 15.02.2020 нужна проверка контрольной суммы на сервере, чтобы не закачивать файл повторно
    private void openFileForWrite() throws IOException {
        out = new FileOutputStream(file.toFile());
    }

    private void startChecksumCounter() {
        try {
            this.md = MessageDigest.getInstance(GlobalSettings.CHECKSUM_PROTOCOL);
        } catch (NoSuchAlgorithmException e) {
            String s = "Checksum protocol" + GlobalSettings.CHECKSUM_PROTOCOL + " doesn't exist";
            LogServiceCommon.APP.fatal(s);
            throw new RuntimeException(s);
        }
    }

    private void closeFileForWrite() {
        try {
            if (out != null) out.close();
        } catch (IOException e) {
            LogServiceCommon.APP.error("File closing error");
        }
    }

    public void setMessageCallback(MessageCallback messageCallback) {
        this.messageCallback = messageCallback;
    }

    private void messageCallback(String msg) {
        if (messageCallback != null) messageCallback.callback(msg);
    }

    private enum State {
        FILENAME_LENGTH, FILE_INFO, FILE_DATA, CHECKSUM, FAIL, SUCCESS
    }
}
