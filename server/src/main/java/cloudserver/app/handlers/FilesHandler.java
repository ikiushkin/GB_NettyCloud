package cloudserver.app.handlers;

import io.netty.channel.ChannelHandlerContext;
import cloudcommon.exceptions.NoEnoughDataException;
import cloudcommon.resources.CommandBytes;
import cloudcommon.services.transfer.CommandSender;
import cloudcommon.services.transfer.FileDownloader;
import cloudcommon.services.transfer.FileUploader;
import cloudserver.app.MainServer;
import cloudserver.resources.ServerSettings;
import cloudserver.services.LogService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FilesHandler {

    private ClientDataHandler clientHandler;
    private ChannelHandlerContext ctx;

    private FileDownloader downloader;
    private FileUploader uploader;

    private Path rootDir;

    FilesHandler(ClientDataHandler clientHandler, ChannelHandlerContext ctx) throws IOException {
        this.clientHandler = clientHandler;
        this.ctx = ctx;
        setUserRepository();
        int bufferSize = MainServer.getSettings().getInt(ServerSettings.DOWNLOAD_BUFFER_SIZE);
        downloader = new FileDownloader(rootDir, clientHandler.getByteBuf(), bufferSize);
        bufferSize = MainServer.getSettings().getInt(ServerSettings.UPLOAD_BUFFER_SIZE);
        uploader = new FileUploader(bufferSize);
    }

    private void setUserRepository() throws IOException {
        rootDir = clientHandler.getServer().getRootDir().resolve(clientHandler.getLogin());
        if (Files.notExists(rootDir)) Files.createDirectory(rootDir);
    }

    void sendFilesList() throws IOException {
        LogService.USERS.info("Sending files list to user", clientHandler.getLogin());
        List<Path> list = Files.list(rootDir).sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        CommandSender.sendCommand(ctx, CommandBytes.FILES_LIST, list.size());
        for (Path file : list) {
            uploader.sendFileInfo(ctx, file);
        }
        LogService.USERS.info("Complete Sending files list to user", clientHandler.getLogin());
    }

    void sendAllFiles() throws IOException {
        LogService.USERS.info("Start uploading all files to user", clientHandler.getLogin());
        sendFilesList();
        sendFiles();
        LogService.USERS.info("Complete uploading all files to user", clientHandler.getLogin());
    }

    private void sendFiles() throws IOException {
        List<Path> files = Files.list(rootDir)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        clientHandler.setWaitingThreadState(true);
        new Thread(() -> {
            for (Path file : files) {
                sendFile(file);
            }
            clientHandler.setWaitingThreadState(false);
        }).start();
    }

    private void sendFile(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        LogService.USERS.info("Uploading: " + file.getFileName().toString());
        if (uploader.upload(ctx, file)) {
            LogService.USERS.info("File upload success", file.getFileName().toString());
        } else {
            LogService.USERS.info("File upload failed", file.getFileName().toString());
        }
    }

    void fileRequest() throws NoEnoughDataException {
        String filename = downloader.downloadFileName();
        downloader.reset();
        LogService.USERS.info("Request file from user", clientHandler.getLogin(), filename);
        Path file = rootDir.resolve(filename);
        clientHandler.setWaitingThreadState(true);
        new Thread(() -> {
            sendFile(file);
            clientHandler.setWaitingThreadState(false);
        }).start();
    }

    void fileDownload() throws NoEnoughDataException {
        int result = downloader.download();
        if (result == 1 || result == -1) {
            downloader.reset();
            clientHandler.downloadFinish();
        }
    }

    void deleteFile() throws NoEnoughDataException, IOException {
        String filename = downloader.downloadFileName();
        downloader.reset();
        LogService.USERS.info("Request deleting file from user", clientHandler.getLogin(), filename);
        Path file = rootDir.resolve(filename);
        Files.deleteIfExists(file);
    }
}
