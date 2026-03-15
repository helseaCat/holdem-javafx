package com.nekocatgato.ui;

import com.nekocatgato.model.Card;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

public class CardView extends StackPane {

    public CardView(Card card) {
        Rectangle bg = new Rectangle(60, 90);
        bg.setFill(Color.WHITE);
        bg.setStroke(Color.BLACK);
        bg.setArcWidth(8);
        bg.setArcHeight(8);

        Text label = new Text(cardLabel(card));
        label.setFill(isRed(card) ? Color.RED : Color.BLACK);

        setAlignment(Pos.CENTER);
        getChildren().addAll(bg, label);
    }

    public CardView() {
        Rectangle bg = new Rectangle(60, 90);
        bg.setFill(Color.DARKBLUE);
        bg.setStroke(Color.BLACK);
        bg.setArcWidth(8);
        bg.setArcHeight(8);
        getChildren().add(bg);
    }

    private String cardLabel(Card card) {
        return rankSymbol(card.getRank()) + suitSymbol(card.getSuit());
    }

    private boolean isRed(Card card) {
        return card.getSuit() == Card.Suit.HEARTS || card.getSuit() == Card.Suit.DIAMONDS;
    }

    private String rankSymbol(Card.Rank rank) {
        return switch (rank) {
            case ACE -> "A"; case KING -> "K"; case QUEEN -> "Q"; case JACK -> "J";
            case TEN -> "10"; default -> String.valueOf(rank.ordinal() + 2);
        };
    }

    private String suitSymbol(Card.Suit suit) {
        return switch (suit) {
            case HEARTS -> "♥"; case DIAMONDS -> "♦";
            case CLUBS -> "♣"; case SPADES -> "♠";
        };
    }
}
