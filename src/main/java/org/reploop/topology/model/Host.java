package org.reploop.topology.model;

import lombok.*;
import org.reploop.topology.core.Accessible;

import javax.persistence.*;

@Entity
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(indexes = {@Index(name = "idx_ip_host", columnList = "host", unique = true)})
public class Host {
    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private Long id;
    private String host;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "serverId")
    @ToString.Exclude
    private Server server;
    /**
     * If we can access this host
     */
    private Accessible accessible;

    public Host(String host) {
        this(host, Accessible.UNKNOWN);
    }

    public Host(String host, Accessible accessible) {
        this.host = host;
        this.accessible = accessible;
    }

    public Host(String host, Server server) {
        this.host = host;
        this.server = server;
    }
}
