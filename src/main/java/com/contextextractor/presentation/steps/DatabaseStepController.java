package com.contextextractor.presentation.steps;

import com.contextextractor.domain.model.DatabaseConfig;
import com.contextextractor.domain.model.TableConfig;
import com.contextextractor.infrastructure.database.PostgresInspector;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class DatabaseStepController extends BaseStepController {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField databaseField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button testConnectionButton;
    @FXML private ProgressIndicator connectionProgress;
    @FXML private Label connectionStatusLabel;

    @FXML private VBox schemaSection;
    @FXML private ChoiceBox<String> schemaComboBox;

    @FXML private VBox tablesSection;
    @FXML private CheckBox defaultExportDdl;
    @FXML private CheckBox defaultExportData;
    @FXML private Spinner<Integer> defaultRowLimit;
    @FXML private TextField tableSearchField;
    @FXML private Label noTablesMatchLabel;
    @FXML private VBox tableListContainer;

    private final PostgresInspector inspector = new PostgresInspector();
    private final List<TableRowView> tableRows = new ArrayList<>();
    private Task<?> currentTask;

    @FXML
    private void initialize() {
        defaultRowLimit.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100000, 5));
        tableSearchField.textProperty().addListener((obs, old, val) -> applyTableFilter(val));
    }

    @Override
    public void onNavigatedTo(int stepIndex) {
        super.onNavigatedTo(stepIndex);
        DatabaseConfig existing = mainController.getDatabaseConfig();
        if (existing != null && !existing.host().isBlank()) {
            hostField.setText(existing.host());
            portField.setText(String.valueOf(existing.port()));
            databaseField.setText(existing.database());
            usernameField.setText(existing.username());
            passwordField.setText(existing.password());

            if (!existing.schema().isBlank()) {
                schemaComboBox.getItems().setAll(existing.schema());
                schemaComboBox.setValue(existing.schema());
                schemaSection.setVisible(true);
                schemaSection.setManaged(true);

                List<TableConfig> saved = mainController.getTableConfigs();
                if (saved != null && !saved.isEmpty()) {
                    for (TableConfig tc : saved) {
                        TableRowView row = new TableRowView(tc);
                        row.wireOnChange(this::saveToMain);
                        tableRows.add(row);
                        tableListContainer.getChildren().add(row.root());
                    }
                    tablesSection.setVisible(true);
                    tablesSection.setManaged(true);
                }
            }
        }
    }

    @FXML
    private void onTestConnection() {
        String host = hostField.getText().trim();
        String database = databaseField.getText().trim();

        if (host.isEmpty() || database.isEmpty()) {
            showStatus("Host and database are required.", false);
            return;
        }

        int port;
        try {
            String portText = portField.getText().trim();
            port = Integer.parseInt(portText.isEmpty() ? "5432" : portText);
        } catch (NumberFormatException e) {
            showStatus("Invalid port number.", false);
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        DatabaseConfig config = new DatabaseConfig(host, port, database, username, password, "");

        cancelCurrentTask();
        clearSchemaAndTables();

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                inspector.testConnection(config);
                return inspector.listSchemas(config);
            }
        };

        bindProgress(task);

        task.setOnSucceeded(e -> {
            showStatus("Connected successfully.", true);
            List<String> schemas = task.getValue();
            schemaComboBox.getItems().setAll(schemas);
            schemaSection.setVisible(true);
            schemaSection.setManaged(true);
        });

        task.setOnFailed(e -> {
            String msg = task.getException().getMessage();
            showStatus("Connection failed: " + (msg != null ? msg : "unknown error"), false);
        });

        currentTask = task;
        Thread thread = new Thread(task, "db-test-connection");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSchemaSelected() {
        String schema = schemaComboBox.getValue();
        if (schema == null) return;

        String host = hostField.getText().trim();
        String database = databaseField.getText().trim();
        int port = parsePort();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        DatabaseConfig config = new DatabaseConfig(host, port, database, username, password, schema);

        cancelCurrentTask();
        tableListContainer.getChildren().clear();
        tableRows.clear();
        tablesSection.setVisible(false);
        tablesSection.setManaged(false);

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return inspector.listTables(config, schema);
            }
        };

        bindProgress(task);

        task.setOnSucceeded(e -> {
            tableSearchField.setText("");
            List<String> tables = task.getValue();
            for (String tableName : tables) {
                TableRowView row = new TableRowView(tableName);
                row.wireOnChange(this::saveToMain);
                tableRows.add(row);
                tableListContainer.getChildren().add(row.root());
            }
            tablesSection.setVisible(true);
            tablesSection.setManaged(true);
            saveToMain();
        });

        task.setOnFailed(e -> {
            String msg = task.getException().getMessage();
            showStatus("Failed to load tables: " + (msg != null ? msg : "unknown error"), false);
        });

        currentTask = task;
        Thread thread = new Thread(task, "db-list-tables");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onApplyToAll() {
        boolean ddl = defaultExportDdl.isSelected();
        boolean data = defaultExportData.isSelected();
        int limit = defaultRowLimit.getValue();
        for (TableRowView row : tableRows) {
            row.setExportDdl(ddl);
            row.setExportData(data);
            row.setRowLimit(limit);
        }
        saveToMain();
    }

    @Override
    protected void onNext() {
        saveToMain();
        mainController.nextStep();
    }

    private void saveToMain() {
        String host = hostField.getText().trim();
        String database = databaseField.getText().trim();
        if (!host.isEmpty() && !database.isEmpty()) {
            int port = parsePort();
            String schema = schemaComboBox.getValue() != null ? schemaComboBox.getValue() : "";
            DatabaseConfig config = new DatabaseConfig(host, port, database,
                    usernameField.getText().trim(), passwordField.getText(), schema);
            mainController.setDatabaseConfig(config);
            mainController.setTableConfigs(tableRows.stream().map(TableRowView::toConfig).toList());
        }
    }

    private int parsePort() {
        try {
            String p = portField.getText().trim();
            return Integer.parseInt(p.isEmpty() ? "5432" : p);
        } catch (NumberFormatException e) {
            return 5432;
        }
    }

    private void showStatus(String message, boolean success) {
        connectionStatusLabel.setText(message);
        connectionStatusLabel.getStyleClass().removeAll("connection-status-success", "connection-status-error");
        connectionStatusLabel.getStyleClass().add(success ? "connection-status-success" : "connection-status-error");
    }

    private void clearSchemaAndTables() {
        schemaComboBox.getItems().clear();
        schemaComboBox.setValue(null);
        schemaSection.setVisible(false);
        schemaSection.setManaged(false);
        tablesSection.setVisible(false);
        tablesSection.setManaged(false);
        tableListContainer.getChildren().clear();
        tableRows.clear();
        tableSearchField.setText("");
    }

    private void applyTableFilter(String filter) {
        String lower = filter == null ? "" : filter.strip().toLowerCase();
        boolean anyVisible = false;
        for (var node : tableListContainer.getChildren()) {
            Object userData = node.getUserData();
            String name = userData instanceof String s ? s.toLowerCase() : "";
            boolean match = lower.isEmpty() || name.contains(lower);
            node.setVisible(match);
            node.setManaged(match);
            if (match) anyVisible = true;
        }
        boolean showNoMatch = !lower.isEmpty() && !anyVisible;
        noTablesMatchLabel.setText(showNoMatch ? "No tables match \"" + filter.strip() + "\"." : "");
        noTablesMatchLabel.setVisible(showNoMatch);
        noTablesMatchLabel.setManaged(showNoMatch);
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
        if (connectionProgress.visibleProperty().isBound()) {
            connectionProgress.visibleProperty().unbind();
            connectionProgress.managedProperty().unbind();
        }
        if (testConnectionButton.disableProperty().isBound()) {
            testConnectionButton.disableProperty().unbind();
            testConnectionButton.setDisable(false);
        }
    }

    private void bindProgress(Task<?> task) {
        connectionProgress.visibleProperty().bind(task.runningProperty());
        connectionProgress.managedProperty().bind(task.runningProperty());
        testConnectionButton.disableProperty().bind(task.runningProperty());
    }

    private static class TableRowView {

        private final String tableName;
        private final VBox root;
        private final VBox detailBox;
        private final CheckBox ddlCheckBox;
        private final CheckBox dataCheckBox;
        private final Spinner<Integer> rowLimitSpinner;
        private final TextField whereField;
        private final TextField orderByField;
        private boolean expanded = false;

        TableRowView(String tableName) {
            this.tableName = tableName;

            Label expandIcon = new Label("▶");
            expandIcon.getStyleClass().add("db-expand-icon");

            Label nameLabel = new Label(tableName);
            nameLabel.getStyleClass().add("db-table-name");

            ddlCheckBox = new CheckBox("DDL");
            ddlCheckBox.setSelected(false);
            dataCheckBox = new CheckBox("Data");
            dataCheckBox.setSelected(false);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox header = new HBox(8, expandIcon, nameLabel, spacer, ddlCheckBox, dataCheckBox);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("db-table-header");
            header.setPadding(new Insets(8, 12, 8, 12));
            header.setCursor(Cursor.HAND);

            whereField = new TextField();
            whereField.setPromptText("WHERE clause (optional, e.g. status = 'active')");
            orderByField = new TextField();
            orderByField.setPromptText("ORDER BY clause (optional, e.g. created_at DESC)");

            rowLimitSpinner = new Spinner<>();
            rowLimitSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100000, 5));
            rowLimitSpinner.setEditable(true);
            rowLimitSpinner.setPrefWidth(100);

            HBox limitRow = new HBox(8, new Label("Row limit:"), rowLimitSpinner);
            limitRow.setAlignment(Pos.CENTER_LEFT);

            VBox whereBox = new VBox(4, new Label("WHERE"), whereField);
            VBox orderByBox = new VBox(4, new Label("ORDER BY"), orderByField);

            detailBox = new VBox(8, whereBox, orderByBox, limitRow);
            detailBox.setPadding(new Insets(8, 12, 12, 32));
            detailBox.getStyleClass().add("db-table-detail");
            detailBox.setVisible(false);
            detailBox.setManaged(false);

            root = new VBox(header, detailBox);
            root.getStyleClass().add("db-table-row");
            root.setUserData(tableName);

            header.setOnMouseClicked(e -> toggleExpand(expandIcon));
        }

        TableRowView(TableConfig config) {
            this(config.tableName());
            ddlCheckBox.setSelected(config.exportDdl());
            dataCheckBox.setSelected(config.exportData());
            rowLimitSpinner.getValueFactory().setValue(config.rowLimit());
            whereField.setText(config.whereClause() != null ? config.whereClause() : "");
            orderByField.setText(config.orderByClause() != null ? config.orderByClause() : "");
        }

        void wireOnChange(Runnable onChange) {
            ddlCheckBox.selectedProperty().addListener((obs, old, val) -> onChange.run());
            dataCheckBox.selectedProperty().addListener((obs, old, val) -> onChange.run());
            rowLimitSpinner.valueProperty().addListener((obs, old, val) -> onChange.run());
            whereField.textProperty().addListener((obs, old, val) -> onChange.run());
            orderByField.textProperty().addListener((obs, old, val) -> onChange.run());
        }

        private void toggleExpand(Label icon) {
            expanded = !expanded;
            icon.setText(expanded ? "▼" : "▶");
            detailBox.setVisible(expanded);
            detailBox.setManaged(expanded);
        }

        VBox root() { return root; }

        void setExportDdl(boolean value) { ddlCheckBox.setSelected(value); }
        void setExportData(boolean value) { dataCheckBox.setSelected(value); }
        void setRowLimit(int value) { rowLimitSpinner.getValueFactory().setValue(value); }

        TableConfig toConfig() {
            return new TableConfig(
                    tableName,
                    ddlCheckBox.isSelected(),
                    dataCheckBox.isSelected(),
                    rowLimitSpinner.getValue(),
                    whereField.getText().trim(),
                    orderByField.getText().trim()
            );
        }
    }
}
