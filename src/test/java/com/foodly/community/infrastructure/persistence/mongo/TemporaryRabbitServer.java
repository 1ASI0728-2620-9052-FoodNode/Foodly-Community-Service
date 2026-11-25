package com.foodly.community.infrastructure.persistence.mongo;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Broker nuevo, puertos y directorios propios; nunca usa variables de conexión de producción. */
final class TemporaryRabbitServer implements AutoCloseable {
    private RabbitMQContainer container;
    private Process process;
    private Process epmd;
    private final List<ProcessHandle> children = new ArrayList<>();
    private int port;
    private final String user = "community_test";
    private final String password = UUID.randomUUID().toString();

    static TemporaryRabbitServer start(Path directory) throws Exception {
        var broker = new TemporaryRabbitServer();
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                broker.container = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.2.9-management"))
                        .withEnv("RABBITMQ_DEFAULT_USER", broker.user).withEnv("RABBITMQ_DEFAULT_PASS", broker.password);
                broker.container.start();
                broker.port = broker.container.getAmqpPort();
            } else broker.startNative(directory);
            return broker;
        } catch (Exception | Error failure) { broker.close(); throw failure; }
    }

    com.rabbitmq.client.ConnectionFactory factory() {
        var factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(container == null ? "127.0.0.1" : container.getHost());
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }

    private void startNative(Path directory) throws Exception {
        Path runtime = Path.of(System.getProperty("community.test.rabbit.runtime", ".cache/rabbit-runtime")).toAbsolutePath();
        Path erlang = runtime.resolve("erlang");
        Path rabbit = runtime.resolve("rabbitmq_server-4.2.9");
        Path script = rabbit.resolve("sbin/rabbitmq-server.bat");
        if (!Files.isRegularFile(script)) throw new IllegalStateException("Rabbit integration tests need Docker or isolated portable runtime; no tests skipped");
        Files.createDirectories(directory);
        directory = directory.toAbsolutePath();
        port = freePort();
        int epmdPort = freePort();
        int distributionPort = freePort();
        Path configuration = directory.resolve("rabbitmq.conf");
        Files.writeString(configuration, "listeners.tcp.default = 127.0.0.1:" + port + "\n");
        Path plugins = directory.resolve("enabled_plugins");
        Files.writeString(plugins, "[].\n");
        var start = new ProcessBuilder("cmd.exe", "/d", "/c", script.toString());
        var env = start.environment();
        env.keySet().removeIf(key -> key.toUpperCase(java.util.Locale.ROOT).startsWith("RABBITMQ_")
                || key.toUpperCase(java.util.Locale.ROOT).startsWith("ERL_"));
        env.put("ERLANG_HOME", erlang.toString());
        env.put("RABBITMQ_HOME", rabbit.toString());
        env.put("RABBITMQ_BASE", directory.toString());
        env.put("RABBITMQ_MNESIA_BASE", directory.resolve("db").toString());
        env.put("RABBITMQ_LOG_BASE", directory.resolve("logs").toString());
        env.put("RABBITMQ_CONFIG_FILE", configuration.toString());
        Path environmentFile = directory.resolve("rabbitmq-env-conf.bat");
        Files.writeString(environmentFile, "@rem Isolated test environment\r\n");
        env.put("RABBITMQ_CONF_ENV_FILE", environmentFile.toString());
        Path advanced = directory.resolve("advanced.config");
        Files.writeString(advanced, "[].\n");
        env.put("RABBITMQ_ADVANCED_CONFIG_FILE", advanced.toString());
        env.put("RABBITMQ_ENABLED_PLUGINS_FILE", plugins.toString());
        env.put("RABBITMQ_NODENAME", "community_test_" + UUID.randomUUID().toString().replace("-", "") + "@localhost");
        env.put("RABBITMQ_NODE_PORT", Integer.toString(port));
        env.put("RABBITMQ_DIST_PORT", Integer.toString(distributionPort));
        env.put("RABBITMQ_DEFAULT_USER", user);
        env.put("RABBITMQ_DEFAULT_PASS", password);
        env.put("ERL_EPMD_PORT", Integer.toString(epmdPort));
        env.put("ERL_EPMD_ADDRESS", "127.0.0.1");
        env.put("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "+S 2:2 -setcookie " + UUID.randomUUID().toString().replace("-", "")
                + " -kernel inet_dist_use_interface {127,0,0,1}");
        Path epmdBinary;
        try (var versions = Files.list(erlang)) {
            epmdBinary = versions.filter(p -> p.getFileName().toString().startsWith("erts-"))
                    .findFirst().orElseThrow().resolve("bin/epmd.exe");
        }
        var epmdStart = new ProcessBuilder(epmdBinary.toString(), "-port", Integer.toString(epmdPort));
        epmdStart.environment().putAll(env);
        epmd = epmdStart.redirectErrorStream(true).redirectOutput(directory.resolve("epmd.log").toFile()).start();
        Path output = directory.resolve("console.log");
        process = start.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            process.descendants().forEach(p -> { if (children.stream().noneMatch(c -> c.pid() == p.pid())) children.add(p); });
            if (!process.isAlive()) throw new IllegalStateException("Isolated Rabbit stopped; inspect " + output);
            if (Files.exists(output) && Files.readString(output).contains("completed with 0 plugins")) {
                System.out.println("Temporary RabbitMQ 4.2.9: fresh data directory, dynamic loopback ports, PID " + process.pid());
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Isolated Rabbit timed out; inspect " + output);
    }

    private static int freePort() throws Exception {
        try (var socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
    }

    @Override public void close() throws Exception {
        if (container != null) container.close();
        if (process != null) {
            process.descendants().forEach(p -> { if (children.stream().noneMatch(c -> c.pid() == p.pid())) children.add(p); });
            children.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            for (var child : children) if (child.isAlive()) child.onExit().get(10, TimeUnit.SECONDS);
        }
        if (epmd != null) { epmd.destroyForcibly(); epmd.waitFor(10, TimeUnit.SECONDS); }
    }
}
