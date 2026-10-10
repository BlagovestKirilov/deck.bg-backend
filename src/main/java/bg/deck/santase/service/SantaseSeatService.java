package bg.deck.santase.service;

import bg.deck.common.constant.Constants;
import bg.deck.common.exception.InvalidCredentialsException;
import bg.deck.common.service.UserAccountService;
import bg.deck.santase.model.SantaseSeat;
import bg.deck.santase.repository.SantaseSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Santase seats: a fresh one for each game, and letting go of a deleted
 * account's. The only class that speaks to {@link SantaseSeatRepository}.
 */
@RequiredArgsConstructor
@Service
public class SantaseSeatService {

    private final SantaseSeatRepository santaseSeatRepository;
    private final UserAccountService userAccountService;

    /**
     * A fresh seat for a new game, under the account's name. The account is
     * checked to exist once, here; from then on the seat is the name.
     */
    @Transactional
    public SantaseSeat newSeatFor(String username) {
        if (!userAccountService.existsByUsername(username)) {
            throw new InvalidCredentialsException(username);
        }
        return santaseSeatRepository.save(SantaseSeat.builder().username(username).build());
    }

    /**
     * A deleted account's seats keep their games, under the tombstone name: a
     * finished game still names who sat in it, and the old name is free for
     * somebody else who must not inherit these seats.
     */
    @Transactional
    public int anonymise(String username) {
        return santaseSeatRepository.rename(username, Constants.DELETED_PLAYER);
    }
}
