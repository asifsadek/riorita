package com.codeforces.riorita;

import org.apache.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public class Riorita {
    private static final Logger logger = Logger.getLogger(Riorita.class);

    private static final byte MAGIC_BYTE = 113;
    private static final byte PROTOCOL_VERSION = 1;
    private static final int MAX_RECONNECT_COUNT = 100;
    private static final long WARN_THRESHOLD_MILLIS = 100;
    private static final int MAX_OPERATION_COUNT_PER_CONNECTION = 1000;

    private static final int RECEIVE_BUFFER_SIZE = 16 * 1024 * 1024;
    private static final int SEND_BUFFER_SIZE = 16 * 1024 * 1024;

    private final Random random = new Random(Riorita.class.hashCode() ^ this.hashCode());

    private Socket socket;
    private final SocketAddress socketAddress;
    private InputStream inputStream;
    private OutputStream outputStream;

    private final String hostAndPort;
    private String keyPrefix = "";
    private final boolean reconnect;
    private AtomicInteger connectionOperationCount = new AtomicInteger();

    public Riorita(String host, int port) {
        this(host, port, true);
    }

    public Riorita(String host, int port, boolean reconnect) {
        this.hostAndPort = host + ":" + port;
        this.reconnect = reconnect;
        socketAddress = new InetSocketAddress(host, port);
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    private String applyKeyPrefix(String key) {
        return keyPrefix + key;
    }

    private void reconnectQuietly() {
        if (socket != null) {
            try {
                logger.warn("Closing socket [" + hostAndPort + "] {" + this + "}.");
                socket.close();
            } catch (IOException e) {
                // No operations.
            }
        }

        try {
            socket = new Socket();
            socket.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
            socket.setSendBufferSize(SEND_BUFFER_SIZE);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setReuseAddress(true);

            socket.connect(socketAddress);

            inputStream = new BufferedInputStream(socket.getInputStream());
            outputStream = new BufferedOutputStream(socket.getOutputStream());

            connectionOperationCount.set(0);
            logger.warn("Connected to " + hostAndPort + ", connectionOperationCount = 0 {" + this + "}.");
        } catch (IOException ignored) {
            // No operations.
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void readExactly(byte[] bytes, int off, int size, long requestId) throws IOException {
        int done = 0;

        while (done < size) {
            int read = inputStream.read(bytes, off + done, size - done);

            if (read < 0) {
                throw new IOException("Unexpected end of inputStream [requestId=" + requestId + "] {" + this + "}.");
            }

            done += read;
        }
    }

    private boolean writeRequestAndReadResponseVerdict(ByteBuffer request, long requestId) throws IOException {
        outputStream.write(request.array());
        outputStream.flush();

        int responseLength = readResponseLength(requestId);
        if (responseLength != 16) {
            throw new IOException("Expected exactly 16 bytes in response [requestId=" + requestId + "] {" + this + "}.");
        }

        return readResponseVerdict(requestId);
    }

    private <T> T runOperation(Operation<T> operation, int size) throws IOException {
        int newConnectionOperationCount = connectionOperationCount.incrementAndGet();

        if (newConnectionOperationCount % 100 == 0) {
            logger.warn("connectionOperationCount: " + connectionOperationCount + " {" + this + "}.");
        }

        if (newConnectionOperationCount > MAX_OPERATION_COUNT_PER_CONNECTION) {
            logger.warn("Reconnect expected because of"
                    + " connectionOperationCount=" + newConnectionOperationCount
                    + " > MAX_OPERATION_COUNT_PER_CONNECTION=" + MAX_OPERATION_COUNT_PER_CONNECTION
                    + " {" + this + "}.");
        }

        long startTimeMills = System.currentTimeMillis();

        try {
            if (!reconnect) {
                T result = operation.run();
                if (result instanceof byte[]) {
                    size += ((byte[]) result).length;
                }
                return result;
            } else {
                IOException exception = null;
                int iteration = 0;

                while (iteration < MAX_RECONNECT_COUNT) {
                    if (connectionOperationCount.get() > MAX_OPERATION_COUNT_PER_CONNECTION) {
                        logger.warn("connectionOperationCount > MAX_OPERATION_COUNT_PER_CONNECTION {" + this + "}.");
                        reconnectQuietly();
                    } else if (socket == null || !socket.isConnected() || socket.isClosed()) {
                        logger.warn("socket == null || !socket.isConnected() || socket.isClosed() {" + this + "}.");
                        reconnectQuietly();
                    }

                    iteration++;

                    if (socket != null && socket.isConnected() && !socket.isClosed()) {
                        try {
                            T result = operation.run();
                            if (result instanceof byte[]) {
                                size += ((byte[]) result).length;
                            }
                            return result;
                        } catch (IOException e) {
                            logger.warn("Can't process operation.", e);
                            exception = e;
                            try {
                                Thread.sleep(iteration * 100);
                            } catch (InterruptedException ignored) {
                                // No operations.
                            }
                            reconnectQuietly();
                        }
                    } else {
                        try {
                            Thread.sleep(iteration * 100);
                        } catch (InterruptedException ignored) {
                            // No operations.
                        }
                    }
                }

                throw exception == null ? new IOException("Can't connect to " + hostAndPort + " {" + this + "}.") : exception;
            }
        } finally {
            long duration = System.currentTimeMillis() - startTimeMills;

            if (duration > WARN_THRESHOLD_MILLIS) {
                logger.warn("Operation " + operation.getType() + " takes " + duration + " ms, id=" + operation.getRequestId() + " [size=" + size + " bytes, " + hostAndPort + "] {" + this + "}.");
                // System.out.println("Operation " + operation.getType() + " takes " + duration + " ms, id=" + operation.getRequestId() + " [size=" + size + " bytes].");
            } else {
                logger.info("Operation " + operation.getType() + " takes " + duration + " ms, id=" + operation.getRequestId() + " [size=" + size + " bytes, " + hostAndPort + "] {" + this + "}.");
                // System.out.println("Operation " + operation.getType() + " takes " + duration + " ms, id=" + operation.getRequestId() + " [size=" + size + " bytes].");
            }
        }
    }

    private ByteBuffer newRequestBuffer(Type type, long requestId, int keyLength, Integer valueLength) {
        int requestLength = 4 // Request length.
                + 1 // Magic byte.
                + 1 // Protocol version.
                + 1 // Type.
                + 8 // Request id.
                + 4 // Key length.
                + keyLength // Key.
                + (valueLength != null ? 4 + valueLength : 0) // Value length + value.
                ;

        ByteBuffer byteBuffer = ByteBuffer.allocate(requestLength).order(ByteOrder.LITTLE_ENDIAN);

        byteBuffer.putInt(requestLength);
        byteBuffer.put(MAGIC_BYTE);
        byteBuffer.put(PROTOCOL_VERSION);
        byteBuffer.put(type.getByte());
        byteBuffer.putLong(requestId);
        byteBuffer.putInt(keyLength);

        return byteBuffer;
    }

    private int readResponseLength(long requestId) throws IOException {
        ByteBuffer responseLengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readExactly(responseLengthBuffer.array(), 0, 4, requestId);
        return responseLengthBuffer.getInt();
    }

    private boolean readResponseVerdict(long requestId) throws IOException {
        int responseHeaderLength = 1 // Magic byte.
                + 1 // Protocol version.
                + 8 // Request id.
                + 1 // Success.
                + 1 // Verdict.
                ;

        ByteBuffer responseBuffer = ByteBuffer.allocate(responseHeaderLength).order(ByteOrder.LITTLE_ENDIAN);
        readExactly(responseBuffer.array(), 0, responseHeaderLength, requestId);

        int magicByte = responseBuffer.get();
        if (magicByte != MAGIC_BYTE) {
            throw new IOException("Invalid magic: expected " + (int) MAGIC_BYTE + ", found " + magicByte + " [requestId=" + requestId + "] {" + this + "}.");
        }

        int protocolVersion = responseBuffer.get();
        if (protocolVersion != PROTOCOL_VERSION) {
            throw new IOException("Invalid protocol: expected " + (int) PROTOCOL_VERSION + ", found " + protocolVersion + " [requestId=" + requestId + "] {" + this + "}.");
        }

        long receivedRequestId = responseBuffer.getLong();
        if (receivedRequestId != requestId) {
            throw new IOException("Invalid request id: expected " + requestId + ", found " + receivedRequestId + " {" + this + "}.");
        }

        byte success = responseBuffer.get();
        if (success != 0 && success != 1) {
            throw new IOException("Operation returned illegal success " + success + " [requestId=" + requestId + "] {" + this + "}.");
        }
        if (success != 1) {
            throw new IOException("Operation didn't return with success [requestId=" + requestId + "] {" + this + "}.");
        }

        byte verdict = responseBuffer.get();
        if (verdict != 0 && verdict != 1) {
            throw new IOException("Operation returned illegal verdict " + verdict + " [requestId=" + requestId + "] {" + this + "}.");
        }

        return verdict == 1;
    }

    private long nextRequestId() {
        return Math.abs(random.nextLong() % 1000000000000000000L);
    }

    private byte[] getStringBytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Can't find UTF-8 {" + this + "}.");
        }
    }

    @SuppressWarnings("unused")
    public boolean ping() throws IOException {
        final long requestId = nextRequestId();
        final ByteBuffer pingBuffer = newRequestBuffer(Type.PING, requestId, 0, null);

        return runOperation(new Operation<Boolean>() {
            @Override
            public Boolean run() throws IOException {
                return writeRequestAndReadResponseVerdict(pingBuffer, requestId);
            }

            @Override
            public Type getType() {
                return Type.PING;
            }

            @Override
            public long getRequestId() {
                return requestId;
            }
        }, 0);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean has(String key) throws IOException {
        key = applyKeyPrefix(key);

        byte[] keyBytes = getStringBytes(key);
        final long requestId = nextRequestId();
        final ByteBuffer hasBuffer = newRequestBuffer(Type.HAS, requestId, keyBytes.length, null);
        hasBuffer.put(keyBytes);

        return runOperation(new Operation<Boolean>() {
            @Override
            public Boolean run() throws IOException {
                return writeRequestAndReadResponseVerdict(hasBuffer, requestId);
            }

            @Override
            public Type getType() {
                return Type.HAS;
            }

            @Override
            public long getRequestId() {
                return requestId;
            }
        }, keyBytes.length);
    }

    @SuppressWarnings("unused")
    public boolean delete(String key) throws IOException {
        key = applyKeyPrefix(key);

        byte[] keyBytes = getStringBytes(key);
        final long requestId = nextRequestId();
        final ByteBuffer deleteBuffer = newRequestBuffer(Type.DELETE, requestId, keyBytes.length, null);
        deleteBuffer.put(keyBytes);

        return runOperation(new Operation<Boolean>() {
            @Override
            public Boolean run() throws IOException {
                return writeRequestAndReadResponseVerdict(deleteBuffer, requestId);
            }

            @Override
            public Type getType() {
                return Type.DELETE;
            }

            @Override
            public long getRequestId() {
                return requestId;
            }
        }, keyBytes.length);
    }

    @SuppressWarnings("WeakerAccess")
    public boolean put(String key, byte[] bytes) throws IOException {
        key = applyKeyPrefix(key);

        byte[] keyBytes = getStringBytes(key);
        final long requestId = nextRequestId();
        final ByteBuffer putBuffer = newRequestBuffer(Type.PUT, requestId, keyBytes.length, bytes.length);
        putBuffer.put(keyBytes);
        putBuffer.putInt(bytes.length);
        putBuffer.put(bytes);

        return runOperation(new Operation<Boolean>() {
            @Override
            public Boolean run() throws IOException {
                return writeRequestAndReadResponseVerdict(putBuffer, requestId);
            }

            @Override
            public Type getType() {
                return Type.PUT;
            }

            @Override
            public long getRequestId() {
                return requestId;
            }
        }, keyBytes.length + bytes.length);
    }

    @SuppressWarnings("WeakerAccess")
    public byte[] get(String key) throws IOException {
        key = applyKeyPrefix(key);

        byte[] keyBytes = getStringBytes(key);
        final long requestId = nextRequestId();
        final ByteBuffer getBuffer = newRequestBuffer(Type.GET, requestId, keyBytes.length, null);
        getBuffer.put(keyBytes);

        return runOperation(new Operation<byte[]>() {
            @Override
            public byte[] run() throws IOException {
                outputStream.write(getBuffer.array());
                outputStream.flush();

                int responseLength = readResponseLength(requestId);
                if (responseLength < 16) {
                    throw new IOException("Expected at least 16 bytes in response, but " + responseLength + " found [requestId=" + requestId + "] {" + this + "}.");
                }

                boolean verdict = readResponseVerdict(requestId);

                if (!verdict) {
                    if (responseLength != 16) {
                        throw new IOException("Expected exactly 16 bytes in response [requestId=" + requestId + "] {" + this + "}.");
                    }

                    return null;
                } else {
                    ByteBuffer valueLengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    readExactly(valueLengthBuffer.array(), 0, 4, requestId);

                    int valueLength = valueLengthBuffer.getInt();
                    if (valueLength < 0) {
                        throw new IOException("Expected positive length of value in response [requestId=" + requestId + "] {" + this + "}.");
                    }

                    ByteBuffer valueBuffer = ByteBuffer.allocate(valueLength).order(ByteOrder.LITTLE_ENDIAN);
                    readExactly(valueBuffer.array(), 0, valueLength, requestId);
                    return valueBuffer.array();
                }
            }

            @Override
            public Type getType() {
                return Type.GET;
            }

            @Override
            public long getRequestId() {
                return requestId;
            }
        }, keyBytes.length);
    }

    public enum Type {
        PING,
        HAS,
        GET,
        PUT,
        DELETE;

        byte getByte() {
            return (byte) (ordinal() + 1);
        }
    }

    private interface Operation<T> {
        T run() throws IOException;
        Type getType();
        long getRequestId();
    }
}
