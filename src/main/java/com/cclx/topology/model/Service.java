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
@Table(indexes = {@Index(name = "idx_service_name_cmd", columnList = "name,cmd", unique = true)})
public class Service {
    @Id
    @GeneratedValue
    private Long id;
    @EqualsAndHashCode.Include
    private String name;
    @EqualsAndHashCode.Include
    private String cmd;
    @OneToMany(mappedBy = "service", fetch = FetchType.EAGER)
    @ToString.Exclude
    private List<Process> processes;
}
