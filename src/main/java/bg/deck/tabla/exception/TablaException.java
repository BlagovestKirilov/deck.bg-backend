package bg.deck.tabla.exception;

import bg.deck.common.exception.GameRuleException;

/**
 * Rule violations coming out of the табла endpoints. One type with named
 * factories rather than five near-identical classes, since they all map to the
 * same 400 response.
 */
public class TablaException extends GameRuleException {

    private TablaException(String message) {
        super(message);
    }

    public static TablaException diceNotRolled() {
        return new TablaException("Заровете не са хвърлени.");
    }

    public static TablaException diceAlreadyRolled() {
        return new TablaException("Заровете вече са хвърлени за този ход.");
    }

    public static TablaException illegalHop() {
        return new TablaException("Невалиден ход.");
    }

    public static TablaException turnNotComplete(int used, int required) {
        return new TablaException(
                "Трябва да изиграете " + required + " зара, изиграни са " + used + ".");
    }

    public static TablaException nothingToUndo() {
        return new TablaException("Няма ход за връщане.");
    }

    public static TablaException notYourTurn() {
        return new TablaException("Не е Ваш ред.");
    }

    public static TablaException openingOver() {
        return new TablaException("Първото хвърляне вече е изиграно.");
    }
}
