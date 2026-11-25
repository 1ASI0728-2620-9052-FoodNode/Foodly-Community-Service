package com.foodly.community.infrastructure.persistence.mongo;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import org.bson.Document;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Solo pruebas: crea un servidor nuevo; nunca consume MONGODB_URI ni se adjunta a otro proceso. */
final class TemporaryMongoServer implements AutoCloseable {

    private MongoDBContainer container;
    private Process process;
    private Thread shutdownHook;
    private String uri;
    private String runtime;

    static TemporaryMongoServer start(Path directory) throws Exception {
        return start(directory, false);
    }

    static TemporaryMongoServer startReplicaSet(Path directory) throws Exception {
        return start(directory, true);
    }

    private static TemporaryMongoServer start(Path directory, boolean replicaSet) throws Exception {
        var server = new TemporaryMongoServer();
        try {
            String mode = System.getProperty("community.test.mongo.runtime", "auto");
            if (!mode.equals("auto") && !mode.equals("docker") && !mode.equals("native")) {
                throw new IllegalArgumentException("Mongo test runtime must be auto, docker or native");
            }
            if (!mode.equals("native") && DockerClientFactory.instance().isDockerAvailable()) {
                server.container = new MongoDBContainer(DockerImageName.parse("mongo:8.0.1"));
                server.container.start();
                server.uri = server.container.getReplicaSetUrl("foodly-community");
                server.runtime = "Testcontainers mongo:8.0.1";
            } else {
                if (mode.equals("docker")) {
                    throw new IllegalStateException("Docker is required by the selected Mongo test runtime");
                }
                server.startNative(directory, replicaSet);
            }
            return server;
        } catch (Exception | Error failure) {
            server.close();
            throw failure;
        }
    }

    String uri() {
        return uri;
    }

    String runtime() {
        return runtime;
    }

    private void startNative(Path directory, boolean replicaSet) throws Exception {
        Path binary = findBinary();
        int port;
        try (var reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            port = reservation.getLocalPort();
        }
        Path data = Files.createDirectory(directory.resolve("data"));
        Path log = directory.resolve("mongod.log");
        var arguments = new java.util.ArrayList<>(java.util.List.of(binary.toString(), "--bind_ip", "127.0.0.1", "--port", Integer.toString(port),
                "--dbpath", data.toString(), "--logpath", log.toString(), "--noauth", "--quiet"));
        if (replicaSet) arguments.addAll(java.util.List.of("--replSet", "community_test"));
        process = new ProcessBuilder(arguments)
                .redirectErrorStream(true).redirectOutput(directory.resolve("process.log").toFile()).start();
        shutdownHook = new Thread(() -> {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }, "foodly-temporary-mongo-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The isolated mongod process stopped before becoming ready");
            }
            if (Files.exists(log) && Files.readString(log).contains("Waiting for connections")) {
                // Solo se conecta tras observar el log del proceso que acabamos de crear.
                uri = "mongodb://127.0.0.1:" + port + "/foodly-community?serverSelectionTimeoutMS=5000&connectTimeoutMS=2000";
                runtime = "Native mongod PID " + process.pid() + " (new process, temporary dbpath, dynamic loopback port)";
                if (replicaSet) {
                    try (var client = MongoClients.create(uri + "&directConnection=true")) {
                        var admin = client.getDatabase("admin");
                        admin.runCommand(new Document("replSetInitiate", new Document("_id", "community_test")
                                .append("members", java.util.List.of(new Document("_id", 0).append("host", "127.0.0.1:" + port)))));
                        long electionDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                        while (!Boolean.TRUE.equals(admin.runCommand(new Document("hello", 1)).getBoolean("isWritablePrimary"))) {
                            if (System.nanoTime() > electionDeadline) throw new IllegalStateException("Temporary replica set election timed out");
                            Thread.sleep(100);
                        }
                    }
                    uri += "&replicaSet=community_test";
                    runtime += " replica set community_test";
                }
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("The isolated mongod did not become ready in time");
    }

    private Path findBinary() {
        String configured = System.getProperty("community.test.mongod", System.getenv("MONGOD_BINARY"));
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                return path;
            }
            throw new IllegalStateException("The configured test mongod binary is not executable");
        }
        String name = System.getProperty("os.name").toLowerCase().contains("win") ? "mongod.exe" : "mongod";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(File.pathSeparator)) {
                Path candidate = Path.of(entry).resolve(name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Mongo integration tests require Docker or a mongod binary; tests are not skipped");
    }

    @Override
    public void close() throws IOException, InterruptedException {
        if (container != null) {
            container.close();
        }
        if (process != null && process.isAlive()) {
            // Shutdown del servidor propio: libera WiredTiger/FTDC antes de limpiar @TempDir en Windows.
            if (uri != null) {
                try (var shutdownClient = MongoClients.create(uri)) {
                    try {
                        shutdownClient.getDatabase("admin").runCommand(new Document("shutdown", 1).append("force", true));
                    } catch (MongoException expectedDisconnect) {
                        // MongoDB cierra la conexión al completar shutdown.
                    }
                }
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The temporary mongod process could not be stopped");
                }
            }
        }
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }
}
