package com.cclx.topology.model;

import com.cclx.topology.core.State;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(indexes = {
        @Index(name = "idx_nf_host_state", columnList = "host,state"),
        @Index(name = "idx_nf_local_host_port", columnList = "localHost,localPort"),
        @Index(name = "idx_nf_host_ppid", columnList = "host,ppid"),
        @Index(name = "idx_nf_host_pid", columnList = "host,pid"),
})
public class NetworkFile {
    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    Long id;
    String host;
    String cmd;
    Integer pid;
    Integer ppid;
    String user;
    String localHost;
    Integer localPort;
    String remoteHost;
    Integer remotePort;
    State state;
    @CreatedDate
    LocalDateTime createdAt;
    @LastModifiedDate
    LocalDateTime updatedAt;
}
