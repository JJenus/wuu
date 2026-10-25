package com.surf.tracker.userauth.api;

import com.surf.tracker.userauth.infrastructure.repository.UserRepository;
import com.surf.tracker.userauth.infrastructure.security.LocalAccountsPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@ConditionalOnProperty(name = "userauth.local-accounts.enabled", havingValue = "true")
public class LocalAccountsController {
    private final UserRepository users;

    public LocalAccountsController(UserRepository users, LocalAccountsPolicy policy) {
        if (!policy.isEnabled()) throw new IllegalStateException("Local accounts are disabled");
        this.users = users;
    }

    @GetMapping("/api/v1/auth/local-accounts")
    public ResponseEntity<AccountsPage> list(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 10000) throw new ResponseStatusException(BAD_REQUEST, "Invalid page");
        var result = users.findByEnabledTrueAndLockedFalse(PageRequest.of(page, 100, Sort.by("email")));
        var accounts = result.map(user -> new Account(user.getEmail(), user.fullName())).getContent();
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .body(new AccountsPage(accounts, result.hasNext() ? page + 1 : null));
    }

    public record Account(String email, String displayName) {}
    public record AccountsPage(List<Account> accounts, Integer nextPage) {}
}
