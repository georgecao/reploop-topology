package com.cclx.topology.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(indexes = {
        @Index(name = "idx_proc_host", columnList = "host"),
        @Index(name = "idx_proc_pid_ppid", columnList = "pid,ppid")
})
public class Proc {
    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private Long id;
    /**
     * master process id. {@link #pid}, group sub-process to master process
     */
    private Integer mid;
    private String host;
    private String user;
    private Integer pid;
    private Integer ppid;
    /**
     * Full command
     */
    @Column(length = 4096)
    private String command;
    /**
     * Short command
     */
    private String cmd;
    /**
     * This process belongs to this service
     */
    @ManyToOne
    @JoinColumn(name = "serviceId")
    private Service service;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    /**
     * All server ports belong to this process.
     */
    @OneToMany(mappedBy = "process")
    private List<ServerPort> serverPorts;
}
