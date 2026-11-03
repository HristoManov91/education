package bg.hristomanov.education.containers;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

/** Test-only real-network topology: caller -> toxiproxy -> nginx. */
final class ToxiproxyScenario implements AutoCloseable {
    private static final int HTTP_PORT = 80;
    private static final int PROXY_PORT = 8666;
    private final Network network = Network.newNetwork();
    private final GenericContainer<?> downstream = new GenericContainer<>(
            DockerImageName.parse("nginx:1.29-alpine"))
            .withNetwork(network)
            .withNetworkAliases("downstream")
            .withExposedPorts(HTTP_PORT)
            .waitingFor(Wait.forHttp("/").forPort(HTTP_PORT));
    private final ToxiproxyContainer toxiproxy = new ToxiproxyContainer(
            DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.12.0"))
            .withNetwork(network)
            .withNetworkAliases("latency-proxy");
    private Proxy proxy;

    public void start() throws IOException {
        downstream.start();
        toxiproxy.start();
        ToxiproxyClient client = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        proxy = client.createProxy("http-downstream", "0.0.0.0:" + PROXY_PORT, "downstream:" + HTTP_PORT);
    }

    public Proxy proxy() { return proxy; }
    public Network network() { return network; }
    public String hostUrl() { return "http://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(PROXY_PORT) + "/"; }
    public String dockerUrl() { return "http://latency-proxy:" + PROXY_PORT + "/"; }

    @Override public void close() {
        try {
            toxiproxy.stop();
        } finally {
            try {
                downstream.stop();
            } finally {
                network.close();
            }
        }
    }
}
