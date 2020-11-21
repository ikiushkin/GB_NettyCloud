package cloudserver.app.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import cloudcommon.exceptions.NoEnoughDataException;
import cloudcommon.resources.CommandBytes;
import cloudcommon.resources.LoginRegError;
import cloudcommon.services.transfer.CommandPackage;
import cloudcommon.services.transfer.CommandSender;
import cloudcommon.services.transfer.FileDownloader;
import cloudserver.services.LogService;
import cloudserver.services.db.AuthService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AuthHandler {

    private ClientDataHandler clientHandler;
    private ChannelHandlerContext ctx;
    private String remoteAddress;
    private String login;

    AuthHandler(ClientDataHandler clientHandler, ChannelHandlerContext ctx) {
        this.clientHandler = clientHandler;
        this.ctx = ctx;
        this.remoteAddress = ctx.channel().remoteAddress().toString();
    }

    void reg(CommandPackage commandPackage) throws NoEnoughDataException {
        IncomingAuthData incomingAuthData = new IncomingAuthData(commandPackage);
        LogService.AUTH.info("Registration attempt", remoteAddress, "Login", incomingAuthData.login);
        LoginRegError error = AuthService.registerAndEchoMsg(incomingAuthData.login, incomingAuthData.pass);
        if (error == null) {
            CommandSender.sendCommand(ctx, CommandBytes.REG_OK);
            LogService.AUTH.info("Registration success", remoteAddress, "Login", incomingAuthData.login);
        } else sendLoginRegError(error);
    }

    void auth(CommandPackage commandPackage) throws NoEnoughDataException, IOException {
        IncomingAuthData incomingAuthData = new IncomingAuthData(commandPackage);
        LogService.AUTH.info("Auth attempt", remoteAddress, "Login", incomingAuthData.login);
        Integer clientId;
        clientId = AuthService.checkLogin(incomingAuthData.login, incomingAuthData.pass);
        if (clientId == null) sendLoginRegError(LoginRegError.INCORRECT_LOGIN_PASS);
        else if (clientHandler.getServer().isUserOnline(incomingAuthData.login))
            sendLoginRegError(LoginRegError.LOGGED_ALREADY);
        else {
            login = incomingAuthData.login;
            CommandSender.sendCommand(ctx, CommandBytes.AUTH_OK, clientId);
            clientHandler.authSuccess();
            LogService.AUTH.info("Auth success", remoteAddress, "Login", login);
        }
    }

    private String passwordFormat(String pass) {
        return pass.trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'");
    }

    private void sendLoginRegError(LoginRegError err) {
        LogService.AUTH.warn(remoteAddress, err.toString());
        CommandSender.sendCommand(ctx, CommandBytes.ERROR, err.ordinal());
    }

    String getLogin() {
        return login;
    }

    private class IncomingAuthData {
        public String login;
        public String pass;

        public IncomingAuthData(CommandPackage commandPackage) throws NoEnoughDataException {
            ByteBuf byteBuf = clientHandler.getByteBuf();
            FileDownloader.checkAvailableData(byteBuf, commandPackage.getByteCommandData(0) + commandPackage.getByteCommandData(1));
            login = byteBuf.readCharSequence(commandPackage.getByteCommandData(0), StandardCharsets.UTF_8).toString();
            pass = passwordFormat(byteBuf.readCharSequence(commandPackage.getByteCommandData(1), StandardCharsets.UTF_8).toString());
        }
    }
}
