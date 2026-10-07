package com.gesstock.gesstock.referentiels.controller;

import com.gesstock.gesstock.referentiels.dto.RoleRequest;
import com.gesstock.gesstock.referentiels.dto.RoleResponse;
import com.gesstock.gesstock.referentiels.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/referentiels/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<RoleResponse> lister() {
        return roleService.lister();
    }

    @GetMapping("/{id}")
    public RoleResponse obtenir(@PathVariable Long id) {
        return roleService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<RoleResponse> creer(@Valid @RequestBody RoleRequest request) {
        RoleResponse response = roleService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public RoleResponse modifier(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return roleService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        roleService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
