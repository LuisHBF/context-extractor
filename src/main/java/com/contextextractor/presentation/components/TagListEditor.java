package com.contextextractor.presentation.components;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class TagListEditor extends VBox {

    private final FlowPane chipsPane = new FlowPane();
    private final TextField addField = new TextField();
    private final List<String> values = new ArrayList<>();
    private final SimpleObjectProperty<List<String>> valuesProperty = new SimpleObjectProperty<>(List.of());

    public TagListEditor() {
        chipsPane.setHgap(6);
        chipsPane.setVgap(6);

        addField.setPromptText("Add pattern...");
        addField.setPrefWidth(180);
        addField.setOnAction(e -> addValue(addField.getText()));

        Button addButton = new Button("Add");
        addButton.getStyleClass().add("browse-button");
        addButton.setOnAction(e -> addValue(addField.getText()));

        HBox addRow = new HBox(8, addField, addButton);
        addRow.setAlignment(Pos.CENTER_LEFT);

        setSpacing(10);
        getChildren().addAll(chipsPane, addRow);
    }

    public void setValues(List<String> newValues) {
        values.clear();
        values.addAll(newValues);
        rebuildChips();
        valuesProperty.set(List.copyOf(values));
    }

    public List<String> getValues() {
        return List.copyOf(values);
    }

    public ObjectProperty<List<String>> valuesProperty() {
        return valuesProperty;
    }

    private void addValue(String raw) {
        String val = raw.trim();
        if (val.isBlank() || values.contains(val)) return;
        values.add(val);
        addField.clear();
        rebuildChips();
        valuesProperty.set(List.copyOf(values));
    }

    private void removeValue(String val) {
        values.remove(val);
        rebuildChips();
        valuesProperty.set(List.copyOf(values));
    }

    private void rebuildChips() {
        chipsPane.getChildren().clear();
        for (String val : values) {
            chipsPane.getChildren().add(buildChip(val));
        }
    }

    private Node buildChip(String value) {
        Label text = new Label(value);
        text.getStyleClass().add("tag-chip-label");

        Button remove = new Button("\u00d7");
        remove.getStyleClass().add("tag-chip-remove");
        remove.setOnAction(e -> removeValue(value));

        HBox chip = new HBox(4, text, remove);
        chip.getStyleClass().add("tag-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(2, 6, 2, 10));
        return chip;
    }
}
