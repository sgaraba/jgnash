/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2013 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.engine.attachment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MessageList;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Handles the details of bi-directional transfer of files between a client and server.
 *
 * @author Craig Cavanaugh
 */
@ChannelHandler.Sharable
public class NettyTransferHandler extends SimpleChannelInboundHandler<String> {

    public static final String FILE_REQUEST = "<FILE_REQUEST>";
    public static final String FILE_STARTS = "<FILE_STARTS>";
    public static final String FILE_ENDS = "<FILE_ENDS>";
    public static final String FILE_CHUNK = "<FILE_CHUNK>";
    public static final String ERROR = "<ERROR>";
    public static final String DELETE = "<DELETE>";
    private static final Logger logger = Logger.getLogger(NettyTransferHandler.class.getName());
    private Map<String, Attachment> fileMap = new ConcurrentHashMap<>();
    private Path attachmentPath;

    /**
     * Netty Handler.  The specified path may be a temporary location for clients or a persistent location for servers.
     *
     * @param attachmentPath Path for attachments.
     */
    public NettyTransferHandler(final Path attachmentPath) {
        this.attachmentPath = attachmentPath;
    }

    @Override
    protected void messageReceived(final ChannelHandlerContext ctx, final String msg) throws Exception {

        if (msg.startsWith(FILE_REQUEST)) {
            sendFile(ctx, attachmentPath + File.separator + msg.substring(FILE_REQUEST.length()));
        } else if (msg.startsWith(FILE_STARTS)) {
            openOutputStream(msg.substring(FILE_STARTS.length()));
        } else if (msg.startsWith(FILE_CHUNK)) {
            writeOutputStream(msg.substring(FILE_CHUNK.length()));
        } else if (msg.startsWith(FILE_ENDS)) {
            closeOutputStream(msg.substring(FILE_ENDS.length()));
        } else if (msg.startsWith(DELETE)) {
            deleteFile(msg.substring(FILE_ENDS.length()));
        }
    }

    private void deleteFile(final String fileName) {
        Path path = Paths.get(attachmentPath + File.separator + fileName);

        if (Files.exists(path)) {
            try {
                Files.delete(path);
            } catch (final IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        for (Attachment object : fileMap.values()) {
            try {
                object.fileOutputStream.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        }

        ctx.fireChannelInactive();    // forward to the next handler in the pipeline
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        logger.log(Level.WARNING, "Unexpected exception from downstream.", cause);
        ctx.close();
    }

    public void sendFile(final Channel channel, final String fileName) {
        Path path = Paths.get(fileName);

        if (Files.exists(path)) {

            if (Files.isDirectory(path)) {
                channel.write(ERROR + "Not a file: " + path + '\n');
                return;
            }

            try (InputStream fileInputStream = Files.newInputStream(path)) {
                channel.write(FILE_STARTS + path.getFileName() + ":" + Files.size(path) + '\n');

                MessageList<String> out = MessageList.newInstance();

                byte[] bytes = new byte[4096];  // leave room for base 64 expansion

                int bytesRead;

                while ((bytesRead = fileInputStream.read(bytes)) != -1) {
                    if (bytesRead > 0) {
                        out.add(FILE_CHUNK + path.getFileName() + ':');
                        out.add(new String(bytes, 0, bytesRead) + '\n');
                    }
                }
                out.add(FILE_ENDS + path.getFileName() + '\n');

                channel.write(out).sync();
            } catch (IOException | InterruptedException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        } else {
            channel.write(ERROR + "File not found: " + path + '\n');
            logger.warning("File not found: " + path);
        }
    }

    private void sendFile(final ChannelHandlerContext ctx, final String msg) {
        sendFile(ctx.channel(), msg);
    }

    private void closeOutputStream(final String msg) {
        Attachment attachment = fileMap.get(msg);

        try {
            attachment.fileOutputStream.close();
            fileMap.remove(msg);
        } catch (final IOException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }

        if (attachment.path.toFile().length() != attachment.fileSize) {
            logger.severe("Invalid file length");
        }
    }

    private void writeOutputStream(final String msg) {
        String[] msgParts = msg.split(":");

        Attachment attachment = fileMap.get(msgParts[0]);

        if (attachment != null) {
            try {
                attachment.fileOutputStream.write(msgParts[1].getBytes());
            } catch (final IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        }
    }

    private void openOutputStream(final String msg) {
        String[] msgParts = msg.split(":");

        final String fileName = msgParts[0];
        final long fileLength = Long.parseLong(msgParts[1]);

        final Path filePath = Paths.get(attachmentPath + File.separator + fileName);

        // Lazy creation of the attachment path if needed
        if (Files.notExists(attachmentPath)) {
            try {
                Files.createDirectories(attachmentPath);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
        }

        try {
            fileMap.put(fileName, new Attachment(filePath, fileLength));
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
        }
    }

    private static class Attachment {
        final Path path;
        final OutputStream fileOutputStream;
        final long fileSize;

        private Attachment(final Path path, long fileSize) throws IOException {
            this.path = path;
            fileOutputStream = Files.newOutputStream(path);

            this.fileSize = fileSize;
        }
    }
}
