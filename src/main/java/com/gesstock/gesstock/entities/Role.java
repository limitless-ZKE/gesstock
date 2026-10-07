package com.gesstock.gesstock.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(length = 30, unique = true, nullable = false)
    private String name;

    @ManyToMany(mappedBy = "roles")
    private Set<Utilisateur> utilisateurs = new HashSet<>();
}