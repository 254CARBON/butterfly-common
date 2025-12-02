package com.z254.butterfly.common.health;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Kafka connectivity.
 * <p>
 * This indicator checks:
 * <ul>
 *     <li>Connectivity to the Kafka cluster</li>
 *     <li>Number of available brokers</li>
 *     <li>Controller availability</li>
 * </ul>
 * <p>
 * Health status:
 * <ul>
 *     <li>UP - Kafka cluster is reachable and has available brokers</li>
 *     <li>DOWN - Cannot connect to Kafka or no brokers available</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * @Bean
 * public KafkaHealthIndicator kafkaHealthIndicator(AdminClient adminClient) {
 *     return new KafkaHealthIndicator(adminClient);
 * }
 * }</pre>
 */
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    private static final long TIMEOUT_MS = 5000;
    
    private final AdminClient adminClient;

    /**
     * Create a new Kafka health indicator.
     *
     * @param adminClient the Kafka admin client for cluster queries
     */
    public KafkaHealthIndicator(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    @Override
    public Health health() {
        try {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            
            // Get cluster info with timeout
            Collection<Node> nodes = clusterResult.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Node controller = clusterResult.controller().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            String clusterId = clusterResult.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            if (nodes.isEmpty()) {
                return Health.down()
                    .withDetail("error", "No Kafka brokers available")
                    .build();
            }
            
            return Health.up()
                .withDetail("clusterId", clusterId)
                .withDetail("brokerCount", nodes.size())
                .withDetail("controllerId", controller != null ? controller.id() : "unknown")
                .withDetail("brokers", nodes.stream()
                    .map(node -> node.host() + ":" + node.port())
                    .toList())
                .build();
            
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return Health.down()
                .withDetail("error", "Failed to connect to Kafka cluster")
                .withDetail("exception", e.getClass().getSimpleName())
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}
