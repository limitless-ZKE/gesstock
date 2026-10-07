package com.gesstock.gesstock.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public CurrentUserResponse currentUser(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            // Spring Security 7 ajoute aussi des autorités de facteur (FACTOR_PASSWORD) qui ne sont pas des rôles.
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring(5))
            .toList();
        return new CurrentUserResponse(authentication.getName(), roles);
    }

    public record CurrentUserResponse(String username, List<String> roles) {}
}
