package com.cclx.topology.model;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Entity
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Server {
    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private Long id;
    private String name;
    @OneToMany(mappedBy = "server", fetch = FetchType.EAGER)
    private List<Host> hosts;

    public Server(String name) {
        this.name = name;
    }
}
