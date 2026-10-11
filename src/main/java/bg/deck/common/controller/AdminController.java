package bg.deck.common.controller;

import bg.deck.common.model.dto.ServiceAvailability;
import bg.deck.common.model.request.AdminPlayerFilter;
import bg.deck.common.model.request.AdminScopeRequest;
import bg.deck.common.model.request.AdminServiceRequest;
import bg.deck.common.model.response.AdminPlayerDetail;
import bg.deck.common.model.response.AdminPlayerPage;
import bg.deck.common.model.response.AdminPlayerRow;
import bg.deck.common.service.AdminService;
import bg.deck.common.util.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin page's API. Open only to an account the database says is an
 * admin — see SecurityConfig — and answered with data, not over the socket.
 */
@RequiredArgsConstructor
@RequestMapping("/admin")
@RestController
public class AdminController {

    private final AdminService adminService;

    /** Every game, whether it is on, and who may see it. */
    @GetMapping("/services")
    public ResponseEntity<List<ServiceAvailability>> services() {
        return ResponseEntity.ok(adminService.services());
    }

    @PutMapping("/services/{code}")
    public ResponseEntity<ServiceAvailability> updateService(@PathVariable String code,
                                                             @Valid @RequestBody AdminServiceRequest request) {
        return ResponseEntity.ok(adminService.updateService(
                AuthenticatedUser.username(), code, request.state(), request.requiredScope()));
    }

    /**
     * A page of players: {@code ?username=&role=&scope=&page=&size=&sort=}.
     * Newest first unless {@code sort} says otherwise ({@code createdAt} or
     * {@code username}, {@code ,asc} or {@code ,desc}).
     */
    @GetMapping("/players")
    public ResponseEntity<AdminPlayerPage> players(
            @ModelAttribute AdminPlayerFilter filter,
            @PageableDefault(size = 8, sort = "createdAt", direction = Sort.Direction.DESC) Pageable page) {
        return ResponseEntity.ok(adminService.players(filter, page));
    }

    /** One player, and what each game knows about them. */
    @GetMapping("/players/{username}")
    public ResponseEntity<AdminPlayerDetail> player(@PathVariable String username) {
        return ResponseEntity.ok(adminService.player(username));
    }

    @PutMapping("/players/{username}/scope")
    public ResponseEntity<AdminPlayerRow> setScope(@PathVariable String username,
                                                   @Valid @RequestBody AdminScopeRequest request) {
        return ResponseEntity.ok(adminService.setScope(AuthenticatedUser.username(), username, request.scope()));
    }
}
