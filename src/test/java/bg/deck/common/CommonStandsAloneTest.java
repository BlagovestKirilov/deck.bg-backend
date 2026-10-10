package bg.deck.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Common stands on nothing the games build.
 *
 * <p>Every game stands on common — accounts, the socket, the error shape — and
 * common may not reach back into any of them: a game is added, or lifted out
 * into a service of its own, without common changing. What common needs from a
 * game it gets through an interface the game implements, or an event the game
 * listens to.
 */
@DisplayName("Common imports no game package")
class CommonStandsAloneTest {

    private static final Path COMMON = Path.of("src/main/java/bg/deck/common");

    private static final Pattern GAME_IMPORT =
            Pattern.compile("^import (static )?bg\\.deck\\.(santase|tabla|belot)\\.", Pattern.MULTILINE);

    @Test
    @DisplayName("no file in common imports santase, табла or belot")
    void commonImportsNoGame() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(COMMON)) {
            offenders = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(CommonStandsAloneTest::importsAGame)
                    .map(file -> COMMON.relativize(file).toString())
                    .toList();
        }
        assertThat(offenders).as("common files importing a game package").isEmpty();
    }

    private static boolean importsAGame(Path file) {
        try {
            return GAME_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }
}
