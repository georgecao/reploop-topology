package com.cclx.topology.model;

import lombok.*;

import javax.persistence.*;

/**
 * https://thorben-janssen.com/lombok-hibernate-how-to-avoid-common-pitfalls/
 */
@Entity
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ServerPort {
    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private Long id;
    @OneToOne
    @JoinColumn(name = "serverId")
    @ToString.Exclude
    private Server server;
    @EqualsAndHashCode.Include
    private Integer port;
    @ManyToOne
    @JoinColumn(name = "processId")
    @ToString.Exclude
    private Proc process;
}
