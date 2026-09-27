package bg.deck.belot.model.response;

import java.util.List;

/**
 * What the table announced this deal, and what it came to.
 *
 * <p>Both sides' announcements are listed, but only one side's count: the
 * better sequence cancels the other's outright, which is a rule players get
 * wrong at the table too. Showing the announcements next to the totals is
 * what makes the cancellation legible instead of mysterious.
 *
 * @param shown           every announcement, in seat order
 * @param northSouthPoints what north and south actually score from theirs
 * @param eastWestPoints   what east and west actually score from theirs
 */
public record BelotDeclarationsView(
        List<BelotDeclarationView> shown,
        int northSouthPoints,
        int eastWestPoints
) {

    public BelotDeclarationsView {
        shown = List.copyOf(shown);
    }

}
