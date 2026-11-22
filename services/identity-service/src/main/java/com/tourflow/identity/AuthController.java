package com.tourflow.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    public record RegisterRequest(@NotBlank @Email(message = "Некорректный email") String email,
                                  @NotBlank @Size(min = 8, max = 72, message = "Пароль от 8 до 72 символов")
                                  @Pattern(regexp = "^(?=.*[A-Za-zА-Яа-я])(?=.*\\d).+$", message = "Пароль должен содержать букву и цифру") String password,
                                  @Size(max = 100) String firstName, @Size(max = 100) String lastName) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record TokenRequest(@NotBlank String token) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest r) {
        service.register(r.email(), r.password(), r.firstName(), r.lastName());
        return Map.of("message", "Мы отправили письмо со ссылкой подтверждения");
    }

    @PostMapping("/confirm")
    public Map<String, String> confirm(@Valid @RequestBody TokenRequest r) { service.confirm(r.token()); return Map.of("message", "Email подтверждён"); }

    @PostMapping("/login")
    public AuthService.TokenPair login(@Valid @RequestBody LoginRequest r) { return service.login(r.email(), r.password()); }

    @PostMapping("/refresh")
    public AuthService.TokenPair refresh(@Valid @RequestBody RefreshRequest r) { return service.refresh(r.refreshToken()); }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest r) { service.logout(r.refreshToken()); }
}
