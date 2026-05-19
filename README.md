# Texas Hold'em

A single-player desktop Texas Hold'em poker game for macOS. Play against rule-based AI opponents with a JavaFX GUI.

## Features

- Full Texas Hold'em game loop (blinds → deal → betting rounds → showdown)
- 3 AI opponents with rule-based decision making
- Per-player action labels (Check, Call, Fold, Bet $X, Raise $X, All In)
- Player elimination and multi-round play
- Clockwise dealer rotation with proper blind posting

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 25 |
| UI | JavaFX 21 |
| Build | Gradle 9.4 (Kotlin DSL) |
| Testing | JUnit Jupiter 6.0.1, jqwik 1.9.2 |
| Utilities | Guava 33.5.0 |

## Getting Started

### Prerequisites

- No manual JDK install needed — Gradle downloads the toolchain automatically via [foojay resolver](https://github.com/gradle/foojay-toolchains)

### Run the game

```bash
./gradlew run
```

### Run tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "com.nekocatgato.engine.HandEvaluatorTest"
```

### Build

```bash
./gradlew build
```

Test reports are generated at `app/build/reports/tests/test/index.html`.

## Project Structure

```
app/src/main/java/com/nekocatgato/
├── App.java                  # JavaFX entry point
├── engine/
│   ├── GameController.java   # Orchestrates round flow
│   ├── GameEventListener.java# Event callback interface
│   └── HandEvaluator.java    # Best 5-card hand evaluation
├── model/
│   ├── Card.java             # Suit + Rank enums
│   ├── Deck.java             # 52-card deck
│   ├── Hand.java             # 2 hole cards per player
│   ├── Board.java            # Community cards
│   ├── GameState.java        # Phase, pot, board, deck
│   ├── Player.java           # Abstract base class
│   ├── HumanPlayer.java      # Waits for UI input
│   └── AIPlayer.java         # Rule-based decisions
└── ui/
    ├── MainMenuView.java     # Start screen
    ├── GameTableView.java    # Main game table
    └── CardView.java         # Single card renderer
```

## Architecture

- **Engine/model** is fully decoupled from UI — no JavaFX dependencies in game logic
- **GameState** is a passive data container; **GameController** drives all mutations
- **Player** is abstract; HumanPlayer and AIPlayer implement `decideAction(GameState, int)`
- Event-driven UI updates via `GameEventListener` interface

## License

[WTFPL](http://www.wtfpl.net/) — Do What The F*ck You Want To Public License.
